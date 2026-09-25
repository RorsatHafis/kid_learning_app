package com.platform.curriculum.dto;

public record CreateSubjectCommand(
    String code, 
    String name, 
    String description) 
{

}