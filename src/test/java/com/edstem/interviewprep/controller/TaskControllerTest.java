package com.edstem.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** End-to-end through the real MVC stack, validation, error handler and database. */
class TaskControllerTest extends IntegrationTest {

  private static final String TASKS = "/api/v1/tasks";

  @Autowired ObjectMapper objectMapper;

  @Nested
  class Create {

    @Test
    void validTask_returns201WithLocationAndDefaults() throws Exception {
      mockMvc
          .perform(
              post(TASKS)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"title": "Write report", "description": "Q3", "dueDate": "2026-10-07"}
                      """))
          .andExpect(status().isCreated())
          .andExpect(header().exists("Location"))
          .andExpect(jsonPath("$.id").isNotEmpty())
          .andExpect(jsonPath("$.title").value("Write report"))
          .andExpect(jsonPath("$.status").value("TODO"))
          .andExpect(jsonPath("$.dueDate").value("2026-10-07"))
          .andExpect(jsonPath("$.createdAt").value("2026-10-07T10:00:00Z"));
    }

    @Test
    void invalidFields_returns400WithMessagePerField() throws Exception {
      String longTitle = "x".repeat(101);

      mockMvc
          .perform(
              post(TASKS)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"title": "%s", "dueDate": "2026-10-06"}
                      """
                          .formatted(longTitle)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors", hasSize(2)))
          .andExpect(jsonPath("$.errors[0].field").value("dueDate"))
          .andExpect(jsonPath("$.errors[0].message").value("dueDate cannot be in the past"))
          .andExpect(jsonPath("$.errors[1].field").value("title"))
          .andExpect(jsonPath("$.errors[1].message").value("title must be at most 100 characters"));
    }

    @Test
    void missingTitle_returns400() throws Exception {
      mockMvc
          .perform(post(TASKS).contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("title"))
          .andExpect(jsonPath("$.errors[0].message").value("title is required"));
    }

    @Test
    void unknownStatus_returns400WithAllowedValues() throws Exception {
      mockMvc
          .perform(
              post(TASKS)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"title": "A", "status": "BLOCKED"}
                      """))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value("status"))
          .andExpect(
              jsonPath("$.errors[0].message")
                  .value("status must be one of [TODO, IN_PROGRESS, DONE]"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
      mockMvc
          .perform(post(TASKS).contentType(MediaType.APPLICATION_JSON).content("{\"title\":"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
  }

  @Nested
  class ReadUpdateDelete {

    @Test
    void getUpdateDelete_roundTrip() throws Exception {
      String id = createTask("Original", "TODO");

      mockMvc
          .perform(get(TASKS + "/" + id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.title").value("Original"));

      mockMvc
          .perform(
              put(TASKS + "/" + id)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"title": "Renamed", "status": "DONE", "dueDate": "2026-12-01"}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.title").value("Renamed"))
          .andExpect(jsonPath("$.status").value("DONE"))
          .andExpect(jsonPath("$.createdAt").value("2026-10-07T10:00:00Z"));

      mockMvc.perform(delete(TASKS + "/" + id)).andExpect(status().isNoContent());

      mockMvc.perform(get(TASKS + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void overdueTask_canBeUpdatedWithoutMovingItsDueDate() throws Exception {
      String id = createTaskDue("2026-10-07");
      clock.advance(Duration.ofDays(2));

      mockMvc
          .perform(
              put(TASKS + "/" + id)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"title": "Late task", "status": "DONE", "dueDate": "2026-10-07"}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("DONE"))
          .andExpect(jsonPath("$.dueDate").value("2026-10-07"));
    }

    @Test
    void update_toADifferentPastDueDate_returns400() throws Exception {
      String id = createTaskDue("2026-10-20");

      mockMvc
          .perform(
              put(TASKS + "/" + id)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"title": "Task", "dueDate": "2026-10-01"}
                      """))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value("dueDate"))
          .andExpect(jsonPath("$.errors[0].message").value("dueDate cannot be in the past"));
    }

    @Test
    void unknownTask_returns404InSameFormat() throws Exception {
      UUID id = UUID.randomUUID();

      mockMvc
          .perform(get(TASKS + "/" + id))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
          .andExpect(jsonPath("$.detail").value("Task " + id + " was not found"));
    }

    @Test
    void updateOrDeleteUnknownTask_returns404() throws Exception {
      String unknown = TASKS + "/" + UUID.randomUUID();

      mockMvc
          .perform(
              put(unknown).contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"A\"}"))
          .andExpect(status().isNotFound());
      mockMvc.perform(delete(unknown)).andExpect(status().isNotFound());
    }

    @Test
    void unsupportedMethod_returns405InSameFormat() throws Exception {
      mockMvc
          .perform(patch(TASKS))
          .andExpect(status().isMethodNotAllowed())
          .andExpect(jsonPath("$.status").value(405))
          .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void invalidId_returns400() throws Exception {
      mockMvc
          .perform(get(TASKS + "/not-a-uuid"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("id"))
          .andExpect(jsonPath("$.errors[0].message").value("id must be a valid UUID"));
    }
  }

  @Nested
  class ListAndFilter {

    @Test
    void filterByStatus_returnsOnlyMatchingTasks() throws Exception {
      String doneId = createTask("Finished", "DONE");
      String todoId = createTask("Pending", "TODO");

      mockMvc
          .perform(get(TASKS).param("status", "DONE"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].status", everyItem(is("DONE"))))
          .andExpect(jsonPath("$[*].id", hasItem(doneId)))
          .andExpect(jsonPath("$[*].id", not(hasItem(todoId))));
    }

    @Test
    void invalidStatusFilter_returns400() throws Exception {
      mockMvc
          .perform(get(TASKS).param("status", "LATER"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("status"));
    }
  }

  private String createTaskDue(String dueDate) throws Exception {
    String body =
        mockMvc
            .perform(
                post(TASKS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\": \"Task\", \"dueDate\": \"%s\"}".formatted(dueDate)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("id").asText();
  }

  private String createTask(String title, String status) throws Exception {
    String body =
        mockMvc
            .perform(
                post(TASKS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s", "status": "%s"}
                        """
                            .formatted(title, status)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode json = objectMapper.readTree(body);
    assertThat(json.get("id").asText()).isNotBlank();
    return json.get("id").asText();
  }
}
