package com.mkalki.cloudtaskapi.entity;

import com.mkalki.cloudtaskapi.enums.Priority;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

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
    private boolean completed;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    public Task(Long id,
                String title,
                String description,
                boolean completed,
                Priority priority ) {

        this.id=id;
        this.title=title;
        this.description=description;
        this.completed=completed;
        this.priority=priority;
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

    public boolean isCompleted(){
        return completed;
    }

    public Priority getPriority(){
        return priority;
    }

    public void setTitle(String title){
        this.title=title;
    }

    public void setDescription(String description){
        this.description=description;
    }

    public void setCompleted(boolean completed){
        this.completed=completed;
    }

    public void setPriority(Priority priority){
        this.priority=priority;
    }

    public Task(){

    }
}
