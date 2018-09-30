package ru.mail.polis.psaer.stage1.blockchain.dto;

public class FunctionParamDTO {

    private String paramType;
    private Object paramObject;

    public FunctionParamDTO(String paramType, Object paramObject) {
        this.paramType = paramType;
        this.paramObject = paramObject;
    }

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType;
    }

    public Object getParamObject() {
        return paramObject;
    }

    public void setParamObject(Object paramObject) {
        this.paramObject = paramObject;
    }
}
