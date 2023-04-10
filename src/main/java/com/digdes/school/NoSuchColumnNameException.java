package com.digdes.school;

public class NoSuchColumnNameException extends RuntimeException{
    public NoSuchColumnNameException(String message){
        super(message);
    }
}
