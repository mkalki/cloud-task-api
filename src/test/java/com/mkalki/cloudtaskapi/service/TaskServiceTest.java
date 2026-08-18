package com.mkalki.cloudtaskapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mkalki.cloudtaskapi.dto.CreateTaskRequest;
import com.mkalki.cloudtaskapi.dto.TaskResponse;
import com.mkalki.cloudtaskapi.dto.UpdateTaskRequest;
import com.mkalki.cloudtaskapi.entity.Task;
import com.mkalki.cloudtaskapi.entity.User;
import com.mkalki.cloudtaskapi.enums.AuditAction;
import com.mkalki.cloudtaskapi.enums.AuditResourceType;
import com.mkalki.cloudtaskapi.enums.Priority;
import com.mkalki.cloudtaskapi.enums.Role;
import com.mkalki.cloudtaskapi.exception.InvalidDateRangeException;
import com.mkalki.cloudtaskapi.exception.TaskAccessDeniedException;
import com.mkalki.cloudtaskapi.exception.TaskNotFoundException;
import com.mkalki.cloudtaskapi.mapper.TaskMapper;
import com.mkalki.cloudtaskapi.repository.TaskRepository;
import com.mkalki.cloudtaskapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getTaskById_shouldReturnTask_whenTaskExists() {
        Task task = new Task();
        when(taskRepository.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(task));
        Task result = taskService.getTaskById(1L);
        assertSame(task,result);
        verify(taskRepository)
                .findByIdAndDeletedFalse(1L);
    }

    @Test
    void getTaskById_shouldThrowException_whenTaskDoesNotExist() {
        when(taskRepository.findByIdAndDeletedFalse(1L))
        .thenReturn(Optional.empty());

        assertThrows(
                TaskNotFoundException.class,
                () -> taskService.getTaskById(1L)
        );
    }

    @Test
    void createTask_shouldCreateTaskSuccessfully() {

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        when(authentication.getName()).thenReturn("testuser");

        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("testuser"))
        .thenReturn(Optional.of(user));

        CreateTaskRequest request = new CreateTaskRequest(
                "Learn Unit Testing",
                "learn Mockito",
                LocalDate.of(2026, 8 ,25),
                Priority.HIGH
        );

        Task savedTask = new Task(
                100L,
                "Learn Unit Testing",
                "learn Mockito",
                Priority.HIGH,
                LocalDate.of(2026,8,25),
                user
        );

        when(taskRepository.save(any(Task.class)))
                .thenReturn(savedTask);

        TaskResponse expectedResponse = new TaskResponse(
                100L,
                "Learn Unit Testing",
                "learn Mockito",
                null,
                Priority.HIGH,
                1L,
                null,
                null
        );

        when(taskMapper.toResponse(savedTask))
                .thenReturn(expectedResponse);

        TaskResponse result = taskService.createTask(request);
        assertSame(expectedResponse, result);

        verify(taskRepository).save(any(Task.class));
        verify(userRepository).findByUsername("testuser");
        verify(auditService).log(
                AuditAction.TASK_CREATED,
                AuditResourceType.TASK,
                100L,
                null
        );
        verify(taskMapper).toResponse(savedTask);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(taskCaptor.capture());

        Task capturedTask = taskCaptor.getValue();
        assertEquals("Learn Unit Testing", capturedTask.getTitle());
        assertEquals("learn Mockito", capturedTask.getDescription());
        assertEquals(Priority.HIGH, capturedTask.getPriority());
        assertEquals(LocalDate.of(2026, 8, 25), capturedTask.getDueDate());
        assertSame(user, capturedTask.getOwner());
    }

    @Test
    void createTask_shouldUseLowPriority_whenPriorityIsNull(){

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        when(authentication.getName()).thenReturn("testuser");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("testuser"))
        .thenReturn(Optional.of(user));

        CreateTaskRequest request = new CreateTaskRequest(
                "Learn Unit Testing",
                "learn Mockito",
                LocalDate.of(2026, 8 ,25),
                null
        );

        Task savedTask = new Task(
                100L,
                "Learn Unit Testing",
                "learn Mockito",
                Priority.LOW,
                LocalDate.of(2026, 8,25),
                null
        );

        when(taskRepository.save(any(Task.class)))
        .thenReturn(savedTask);

        when(taskMapper.toResponse(savedTask))
        .thenReturn(new TaskResponse(
                100L,
                "Learn Unit Testing",
                "learn Mockito",
                null,
                Priority.LOW,
                1L,
                null,
                null
        ));

        taskService.createTask(request);

        ArgumentCaptor<Task> taskCaptor =
                ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(taskCaptor.capture());

        Task capturedTask = taskCaptor.getValue();

        assertEquals(Priority.LOW, capturedTask.getPriority());

    }

    @Test
    void createTask_shouldThrowException_whenUserDoesNotExist() {

        when(authentication.getName()).thenReturn("unkown");

        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("unkown"))
        .thenReturn(Optional.empty());

        CreateTaskRequest request = new CreateTaskRequest(
                "Learn Unit Testing",
                "learn Mockito",
                LocalDate.of(2026, 8 ,25),
                Priority.HIGH
        );

        assertThrows(
                UsernameNotFoundException.class,
                () -> taskService.createTask(request)
        );
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void getTaskResponseById_shouldReturnTaskResponse_whenUserOwnsTask() {

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        when(authentication.getName()).thenReturn("testuser");
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        Task task = new Task(
                100L,
                "Learn Unit Testing",
                "learn Mockito",
                Priority.HIGH,
                LocalDate.of(2026, 8 ,25),
                user
        );

        when(taskRepository.findByIdAndDeletedFalse(100L))
        .thenReturn(Optional.of(task));

        TaskResponse expectedResponse = new TaskResponse(
                100L,
                "Learn Unit Testing",
                "learn Mockito",
                null,
                Priority.HIGH,
                1L,
                null,
                null
        );

        when(taskMapper.toResponse(task))
        .thenReturn(expectedResponse);

        TaskResponse result = taskService.getTaskResponseById(100L);

        assertSame(expectedResponse, result);

        verify(taskRepository)
        .findByIdAndDeletedFalse(100L);

        verify(taskMapper)
        .toResponse(task);
    }

    @Test
    void getTaskResponseById_shouldThrowException_whenUserDoesNotOwnTask() {
        User currentUser = new User();
        currentUser.setId(2L);
        currentUser.setUsername("otheruser");
        currentUser.setRole(Role.USER);

        when(authentication.getName()).thenReturn("otheruser");
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("otheruser"))
        .thenReturn(Optional.of(currentUser));

        User owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");

        Task task = new Task(
                100L,
                "Learn Unit Testing",
                "learn Mockito",
                Priority.HIGH,
                LocalDate.of(2026, 8 ,25),
                owner
        );
        when(taskRepository.findByIdAndDeletedFalse(100L))
        .thenReturn(Optional.of(task));

        assertThrows(
                TaskAccessDeniedException.class,
                () -> taskService.getTaskResponseById(100L)
        );

        verify(taskMapper, never())
                .toResponse(task);
    }

    @Test
    void getTaskResponseById_shouldReturnResponse_whenUserIsAdmin() {
        User admin = new User();
        admin.setId(2L);
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);

        when(authentication.getName()).thenReturn("admin");
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("admin"))
        .thenReturn(Optional.of(admin));

        User owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");

        Task task = new Task(
                100L,
                "Learn Unit Testing",
                "learn Mockito",
                Priority.HIGH,
                LocalDate.of(2026, 8 ,25),
                owner
        );

        when(taskRepository.findByIdAndDeletedFalse(100L))
        .thenReturn(Optional.of(task));

        TaskResponse expectedResponse = new TaskResponse(
                100L,
                "Learn Unit Testing",
                "learn Mockito",
                null,
                Priority.HIGH,
                1L,
                null,
                null
        );

        when(taskMapper.toResponse(task))
        .thenReturn(expectedResponse);

        TaskResponse result = taskService.getTaskResponseById(100L);

        assertSame(expectedResponse, result);

        verify(taskMapper)
        .toResponse(task);
    }

    @Test
    void updateTask_shouldUpdateTaskSuccessfully() {

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole(Role.USER);

        when(authentication.getName())
                .thenReturn("testuser");

        when(securityContext.getAuthentication())
                .thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        Task task = new Task(
                100L,
                "Old Title",
                "Description",
                Priority.HIGH,
                LocalDate.of(2026, 8, 25),
                user
        );

        when(taskRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(task));

        UpdateTaskRequest request = new UpdateTaskRequest(
                "New Title",
                null,
                null,
                null,
                null
        );

        when(taskRepository.save(task))
                .thenReturn(task);

        TaskResponse expectedResponse = new TaskResponse(
                100L,
                "New Title",
                "Description",
                null,
                Priority.HIGH,
                1L,
                null,
                null
        );

        when(taskMapper.toResponse(task))
                .thenReturn(expectedResponse);

        TaskResponse result = taskService.updateTask(100L, request);

        assertSame(expectedResponse, result);
        assertEquals("New Title", task.getTitle());

        verify(taskRepository).save(task);

        verify(auditService).log(
                eq(AuditAction.TASK_UPDATED),
                eq(AuditResourceType.TASK),
                eq(100L),
                any(ObjectNode.class)
        );

        ArgumentCaptor<ObjectNode> auditCaptor =
                ArgumentCaptor.forClass(ObjectNode.class);

        verify(auditService).log(
                eq(AuditAction.TASK_UPDATED),
                eq(AuditResourceType.TASK),
                eq(100L),
                auditCaptor.capture()
        );

        ObjectNode changes = auditCaptor.getValue();

        assertEquals("Old Title", changes.get("title").get("old").asText());
        assertEquals("New Title", changes.get("title").get("new").asText());
    }

    @Test
    void updateTask_shouldNotSaveOrAudit_whenNothingChanges() {

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole(Role.USER);

        when(authentication.getName()).thenReturn("testuser");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        Task task = new Task(
                100L,
                "Existing Title",
                "Description",
                Priority.HIGH,
                LocalDate.of(2026, 8, 25),
                user
        );

        when(taskRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(task));

        // Every field is null → nothing is requested to change
        UpdateTaskRequest request = new UpdateTaskRequest(
                null,
                null,
                null,
                null,
                null
        );

        TaskResponse expectedResponse = new TaskResponse(
                100L,
                "Existing Title",
                "Description",
                null,
                Priority.HIGH,
                1L,
                null,
                null
        );

        when(taskMapper.toResponse(task))
                .thenReturn(expectedResponse);

        TaskResponse result = taskService.updateTask(100L, request);

        assertSame(expectedResponse, result);

        verify(taskRepository, never()).save(any(Task.class));
        verify(auditService, never()).log(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void updateTask_shouldThrowException_whenUserDoesNotOwnTask() {

        User currentUser = new User();
        currentUser.setId(2L);
        currentUser.setUsername("otheruser");
        currentUser.setRole(Role.USER);

        when(authentication.getName()).thenReturn("otheruser");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("otheruser"))
                .thenReturn(Optional.of(currentUser));

        User owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");

        Task task = new Task(
                100L,
                "Old Title",
                "Description",
                Priority.HIGH,
                LocalDate.of(2026, 8, 25),
                owner
        );

        when(taskRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(task));

        UpdateTaskRequest request = new UpdateTaskRequest(
                "Hacked Title",
                null,
                null,
                null,
                null
        );

        assertThrows(
                TaskAccessDeniedException.class,
                () -> taskService.updateTask(100L, request)
        );

        verify(taskRepository, never()).save(any(Task.class));
        verify(auditService, never()).log(
                any(), any(), any(), any()
        );
    }

    @Test
    void updateTask_shouldThrowException_whenTaskDoesNotExist() {

        when(taskRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.empty());

        UpdateTaskRequest request = new UpdateTaskRequest(
                "New Title",
                null,
                null,
                null,
                null
        );

        assertThrows(
                TaskNotFoundException.class,
                () -> taskService.updateTask(100L, request)
        );

        verify(taskRepository, never()).save(any(Task.class));
        verify(auditService, never()).log(
                any(), any(), any(), any()
        );
    }

    @Test
    void deleteTask_shouldSoftDeleteTaskSuccessfully() {

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole(Role.USER);

        when(authentication.getName()).thenReturn("testuser");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        Task task = new Task(
                100L,
                "Task to Delete",
                "Description",
                Priority.HIGH,
                LocalDate.of(2026, 8, 25),
                user
        );

        when(taskRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(task));

        when(taskRepository.save(task))
                .thenReturn(task);

        taskService.deleteTask(100L);

        assertTrue(task.isDeleted());
        assertNotNull(task.getDeletedAt());

        verify(taskRepository).save(task);

        verify(auditService).log(
                eq(AuditAction.TASK_DELETED),
                eq(AuditResourceType.TASK),
                eq(100L),
                eq(null)
        );
    }

    @Test
    void deleteTask_shouldThrowException_whenUserDoesNotOwnTask() {

        User currentUser = new User();
        currentUser.setId(2L);
        currentUser.setUsername("otheruser");
        currentUser.setRole(Role.USER);

        when(authentication.getName()).thenReturn("otheruser");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("otheruser"))
                .thenReturn(Optional.of(currentUser));

        User owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");

        Task task = new Task(
                100L,
                "Protected Task",
                "Description",
                Priority.HIGH,
                LocalDate.of(2026, 8, 25),
                owner
        );

        when(taskRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(task));

        assertThrows(
                TaskAccessDeniedException.class,
                () -> taskService.deleteTask(100L)
        );

        assertFalse(task.isDeleted());

        verify(taskRepository, never()).save(any(Task.class));

        verify(auditService, never()).log(
                any(), any(), any(), any()
        );
    }

    @Test
    void getTasks_shouldReturnOnlyCurrentUsersTasks() {

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole(Role.USER);

        when(authentication.getName()).thenReturn("testuser");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        Pageable pageable = PageRequest.of(0, 10);

        Page<Task> taskPage = new PageImpl<>(List.of());

        when(taskRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(taskPage);

        taskService.getTasks(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                pageable
        );

        verify(taskRepository).findAll(
                any(Specification.class),
                eq(pageable)
        );
    }

    @Test
    void getTasks_shouldThrowException_whenDateRangeIsInvalid() {

        LocalDate dueAfter = LocalDate.of(2026, 8, 30);
        LocalDate dueBefore = LocalDate.of(2026, 8, 20);

        Pageable pageable = PageRequest.of(0, 10);

        assertThrows(
                InvalidDateRangeException.class,
                () -> taskService.getTasks(
                        null,
                        null,
                        null,
                        dueBefore,
                        dueAfter,
                        null,
                        null,
                        pageable
                )
        );

        verifyNoInteractions(taskRepository);
    }
}
