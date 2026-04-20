package com.example.face2info.entity.internal;

import com.example.face2info.entity.response.SocialAccount;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合阶段内部结果对象。
 * 统一承载人物信息、社交账号以及告警信息。
 */
@Schema(description = "服务内部使用的聚合结果")
public class AggregationResult {

    @Schema(description = "聚合得到的人物主体信息")
    private PersonAggregate person = new PersonAggregate();

    @Schema(description = "聚合得到的社交账号列表")
    private List<SocialAccount> socialAccounts = new ArrayList<>();

    @Schema(description = "聚合过程中记录的错误信息")
    private List<String> errors = new ArrayList<>();

    @Schema(description = "聚合过程中记录的告警信息")
    private List<String> warnings = new ArrayList<>();

    public PersonAggregate getPerson() {
        return person;
    }

    public AggregationResult setPerson(PersonAggregate person) {
        this.person = person;
        return this;
    }

    public List<SocialAccount> getSocialAccounts() {
        return socialAccounts;
    }

    public AggregationResult setSocialAccounts(List<SocialAccount> socialAccounts) {
        this.socialAccounts = socialAccounts;
        return this;
    }

    public List<String> getErrors() {
        return errors;
    }

    public AggregationResult setErrors(List<String> errors) {
        this.errors = errors;
        return this;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public AggregationResult setWarnings(List<String> warnings) {
        this.warnings = warnings;
        return this;
    }
}
