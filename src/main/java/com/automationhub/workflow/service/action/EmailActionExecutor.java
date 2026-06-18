package com.automationhub.workflow.service.action;

import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(EmailActionExecutor.class);

    @Override
    public boolean supports(ActionType type) {
        return type == ActionType.EMAIL;
    }

    @Override
    public ActionResult execute(Action action) {
        log.info("Email action stub invoked: actionId={} config={}", action.getId(), action.getConfig());
        return ActionResult.ok("email: simulated send");
    }
}
