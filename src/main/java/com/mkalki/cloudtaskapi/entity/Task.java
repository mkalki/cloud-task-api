package com.mkalki.cloudtaskapi.entity;

import com.mkalki.cloudtaskapi.enums.Priority;
import com.mkalki.cloudtaskapi.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

@Entity
@Table(name="tasks")
public class Task extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String title;
    private String description;
    private LocalDate dueDate;
    @Enumerated(EnumType.STRING)
    private Status status = Status.TODO;


    @Enumerated(EnumType.STRING)
    private Priority priority;


    public Task(Long id,
                String title,
                String description,
                Status status,
                Priority priority,
                LocalDate dueDate) {

        this.id=id;
        this.title=title;
        this.description=description;
        this.status=status;
        this.priority=priority;
        this.dueDate=dueDate;
    }

    public Long getId(){
        return id;
    }

    public String getTitle(){
        return title;
    }

    public String getDescription(){
        return description;
    }

    public Status getStatus(){
        return status;
    }

    public Priority getPriority(){
        return priority;
    }

    public LocalDate getDueDate(){
        return dueDate;
    }

    public void setTitle(String title){
        this.title=title;
    }

    public void setDescription(String description){
        this.description=description;
    }

    public void setStatus(Status status){
        this.status=status;
    }

    public void setPriority(Priority priority){
        this.priority=priority;
    }

    public void setDueDate(LocalDate dueDate){
        this.dueDate=dueDate;
    }

    public Task(){

    }
}
