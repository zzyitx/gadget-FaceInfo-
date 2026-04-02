package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "人物基础信息")
public class PersonBasicInfoResponse {

    @JsonProperty("birth_date")
    @Schema(description = "出生年月或出生日期")
    private String birthDate;

    @Schema(description = "毕业院校或教育经历")
    private List<String> education = new ArrayList<>();

    @Schema(description = "身份职业")
    private List<String> occupations = new ArrayList<>();

    @Schema(description = "人物生平")
    private List<String> biographies = new ArrayList<>();

    public String getBirthDate() {
        return birthDate;
    }

    public PersonBasicInfoResponse setBirthDate(String birthDate) {
        this.birthDate = birthDate;
        return this;
    }

    public List<String> getEducation() {
        return education;
    }

    public PersonBasicInfoResponse setEducation(List<String> education) {
        this.education = education;
        return this;
    }

    public List<String> getOccupations() {
        return occupations;
    }

    public PersonBasicInfoResponse setOccupations(List<String> occupations) {
        this.occupations = occupations;
        return this;
    }

    public List<String> getBiographies() {
        return biographies;
    }

    public PersonBasicInfoResponse setBiographies(List<String> biographies) {
        this.biographies = biographies;
        return this;
    }
}
