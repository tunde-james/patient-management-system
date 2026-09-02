package com.devtunde.authservice.mapper;

import com.devtunde.authservice.dto.RegisterReqDto;
import com.devtunde.authservice.dto.RegisterResDto;
import com.devtunde.authservice.model.User;

public final class UserMapper {

    private UserMapper() {}

    public static RegisterResDto toDTO(User user) {

        return new RegisterResDto(user.getId(), user.getEmail(), "Account created");
    }

    public static User toModel(RegisterReqDto registerReqDto, String passwordHash) {

        return new User(registerReqDto.email(), passwordHash);
    }
}
