package com.automationhub.workflow.service.action;

import com.automationhub.workflow.entity.ActionType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ActionExecutorRegistry {

    private final Map<ActionType, ActionExecutor> executors;

    public ActionExecutorRegistry(List<ActionExecutor> executors) {
        Map<ActionType, ActionExecutor> map = new EnumMap<>(ActionType.class);
        for (ActionType type : ActionType.values()) {
            List<ActionExecutor> matches = executors.stream().filter(e -> e.supports(type)).toList();
            if (matches.size() > 1) {
                throw new IllegalStateException("Multiple ActionExecutor beans support " + type + ": " + matches);
            }
            if (matches.size() == 1) {
                map.put(type, matches.get(0));
            }
        }
        this.executors = Map.copyOf(map);
    }

    public ActionExecutor resolve(ActionType type) {
        ActionExecutor executor = executors.get(type);
        if (executor == null) {
            throw new IllegalStateException("No ActionExecutor registered for type " + type);
        }
        return executor;
    }
}
