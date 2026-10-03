package com.banking.api.dto;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerCreateRequest {
  private String addrln1 ;
    private String country;
    private String ccateg;
    private String fullname;
    private String media ;
    private String loc;
    private Date dob;
    private String sname;
    private String nlty ;
    private String lang;
    private String gendr;
    private String  birthdate;
}
