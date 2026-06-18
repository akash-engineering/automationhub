package com.automationhub.workflow.service.action;

import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SlackActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(SlackActionExecutor.class);

    @Override
    public boolean supports(ActionType type) {
        return type == ActionType.SLACK;
    }

    @Override
    public ActionResult execute(Action action) {
        log.info("Slack action stub invoked: actionId={} config={}", action.getId(), action.getConfig());
        return ActionResult.ok("slack: simulated send");
    }
}
