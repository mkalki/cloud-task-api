package com.mkalki.cloudtaskapi.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import com.mkalki.cloudtaskapi.model.Greeting;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public Greeting hello(){
        Greeting greeting=new Greeting("hello world","mkalki");
        return greeting;
    }
}
