package com.automationhub.workflow.service.action;

import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.ActionType;

public interface ActionExecutor {

    boolean supports(ActionType type);

    ActionResult execute(Action action);
}
