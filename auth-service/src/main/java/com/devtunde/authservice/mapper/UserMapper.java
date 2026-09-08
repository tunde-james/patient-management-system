package com.devtunde.authservice.mapper;

import com.devtunde.authservice.dto.LoginResDto;
import com.devtunde.authservice.dto.RegisterResDto;
import com.devtunde.authservice.model.User;

public final class UserMapper {

    private UserMapper() {}

    public static RegisterResDto toDTO(User user) {

        return new RegisterResDto(user.getId(), user.getEmail(), "Account created");
    }

    public static LoginResDto toLoginDTO(User user) {

        return new LoginResDto(user.getId(), user.getEmail(), "Login successful");
    }
}
