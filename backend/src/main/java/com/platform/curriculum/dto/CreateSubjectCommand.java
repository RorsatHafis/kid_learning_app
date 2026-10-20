package com.platform.curriculum.dto;

/**
 * What is needed to create a subject.
 */
public record CreateSubjectCommand(
    String code, 
    String name, 
    String description) 
{

}