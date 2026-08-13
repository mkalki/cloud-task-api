package com.mkalki.cloudtaskapi.controller;

import com.mkalki.cloudtaskapi.dto.CreateTaskRequest;
import com.mkalki.cloudtaskapi.dto.TaskResponse;
import com.mkalki.cloudtaskapi.dto.UpdateTaskRequest;
import com.mkalki.cloudtaskapi.enums.Priority;
import com.mkalki.cloudtaskapi.enums.Status;
import com.mkalki.cloudtaskapi.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name="Tasks",
        description="Operations for managing tasks")
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(summary="Retrieve a task by ID.",
            description="Returns the task with the specified ID. Returns 404 Not Found if the task does not exist.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",description = "Returns the requested task."),
            @ApiResponse(responseCode = "404",description = "The requested task was not found.")
    })
    @GetMapping("/{id}")
    public TaskResponse getTaskById(@Parameter(description = "Unique identifier of the task.")
            @PathVariable Long id) {
        return taskService.getTaskResponseById(id);
    }

    @Operation(summary="Retrieve tasks.",
            description="Returns a paginated list of tasks. Supports pagination, sorting, filtering, and keyword search.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",description = "Returns a paginated list of tasks."),
            @ApiResponse(responseCode = "400",description = "The request contains invalid query parameters.")
    })
    @GetMapping
    public Page<TaskResponse> getTasks(
            @Parameter(description="Zero-based page index. Defaults to 0 if not specified.")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of tasks to return per page. Defaults to 10 if not specified.")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Specifies the field and sort direction in the format field,direction, " +
                    "for example id,asc or title,desc. " +
                    "Supported fields include id, title, priority, status, and dueDate. " +
                    "Defaults to id,asc if not specified." +
                    " Use `asc` for ascending order and `desc` for descending order.")
            @RequestParam(defaultValue = "id,asc") String sort,
            @Parameter(description = "Filters tasks by their status. Supported values are TODO, IN_PROGRESS, and COMPLETED. " +
                            "If not specified, tasks of all statuses are returned.")
            @RequestParam(required = false) Status status,
            @Parameter(description = "Filters tasks whose title matches the specified value. " +
                    "If not specified, no title filter is applied.")
            @RequestParam(required = false) String title,
            @Parameter(description = "Returns tasks that are due on the specified date.")
            @RequestParam(required = false) LocalDate dueDate,
            @Parameter(description = "Returns tasks that are due before the specified date.")
            @RequestParam(required = false) LocalDate dueBefore,
            @Parameter(description = "Returns tasks that are due after the specified date.")
            @RequestParam(required = false) LocalDate dueAfter,
            @Parameter(description = "Filters tasks by priority. Supported values are LOW, MEDIUM and HIGH." +
                    "If not specified, tasks of all priorities are returned")
            @RequestParam(required = false) Priority priority,
            @Parameter(description = "Searches tasks by title or description." +
                    "If not specified, all tasks are returned.")
            @RequestParam(required = false) String search
            ) {

        String[] sortParts = sort.split(",");

        Sort sortObject;

        if(sortParts.length == 1){
            sortObject= Sort.by(sortParts[0]).ascending();
        }
        else if("desc".equalsIgnoreCase(sortParts[1])){
            sortObject=Sort.by(sortParts[0]).descending();
        }else {
            sortObject=Sort.by(sortParts[0]).ascending();
        }
        Pageable pageable = PageRequest.of(page, size ,sortObject);
        return taskService.getTasks(status,title,
                dueDate,
                dueBefore,
                dueAfter,
                priority,
                search,
                pageable);
    }
    @Operation(summary="Create a new task.",
            description = "Creates a new task from the request payload. Default values are applied by the server where applicable.")
    @ApiResponses(value = {
            @ApiResponse(responseCode="201",description = "Task created successfully"),
            @ApiResponse(responseCode ="400",description = "The request contains invalid or missing task data.")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@Valid @RequestBody CreateTaskRequest request){
        return taskService.createTask(request);
    }

    @Operation(summary = "Update an existing task.",
            description ="Updates the task with the specified ID. Returns an error if the task is not found." )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",description = "Task updated successfully."),
            @ApiResponse(responseCode = "400",description = "The request contains invalid or missing task data."),
            @ApiResponse(responseCode = "404",description = "The requested task was not found.")
    })
    @PutMapping("/{id}")
    public TaskResponse updateTask( @Parameter(description = "Unique identifier of the task.")
                            @PathVariable Long id,
                            @Valid @RequestBody UpdateTaskRequest request){
        return taskService.updateTask(id,request);
    }

    @Operation(summary = "Delete a task.",
            description = "Marks the specified task as deleted so it is no longer returned by task queries.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",description = "Task deleted successfully. No content is returned."),
            @ApiResponse(responseCode = "400",description = "The request contains invalid or missing task data."),
            @ApiResponse(responseCode = "404",description = "The requested task was not found.")
    })

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@Parameter(description = "Unique identifier of the task.")
            @PathVariable Long id){
        taskService.deleteTask(id);
    }


}
