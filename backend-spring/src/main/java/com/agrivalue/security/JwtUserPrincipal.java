package com.agrivalue.security;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtUserPrincipal {
    private Integer id;
    private String email;
    private String role;
    private String name;
}
