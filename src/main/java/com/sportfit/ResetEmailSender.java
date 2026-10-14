package com.sportfit;
public interface ResetEmailSender { boolean available(); void sendReset(String email,String url); }
