package com.automationhub.workflow.service.action;

public record ActionResult(boolean success, String message) {

    public static ActionResult ok(String message) {
        return new ActionResult(true, message);
    }

    public static ActionResult failed(String message) {
        return new ActionResult(false, message);
    }
}
