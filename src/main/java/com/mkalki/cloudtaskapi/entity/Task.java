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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;


    public Task(Long id,
                String title,
                String description,
                Priority priority,
                LocalDate dueDate,
                User owner) {

        this.id=id;
        this.title=title;
        this.description=description;
        this.status=status;
        this.priority=priority;
        this.dueDate=dueDate;
        this.owner=owner;
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

    public User getOwner(){
        return owner;
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

    public void setOwner(User owner){
        this.owner=owner;
    }

    public Task(){

    }
}
