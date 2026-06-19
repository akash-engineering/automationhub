package com.automationhub.workflow.service.action;

import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.ActionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionExecutorRegistryTest {

    @Test
    void resolves_each_action_type_to_its_supporting_executor() {
        StubExecutor slack = new StubExecutor(ActionType.SLACK);
        StubExecutor email = new StubExecutor(ActionType.EMAIL);
        StubExecutor http = new StubExecutor(ActionType.HTTP);

        ActionExecutorRegistry registry = new ActionExecutorRegistry(List.of(slack, email, http));

        assertThat(registry.resolve(ActionType.SLACK)).isSameAs(slack);
        assertThat(registry.resolve(ActionType.EMAIL)).isSameAs(email);
        assertThat(registry.resolve(ActionType.HTTP)).isSameAs(http);
    }

    @Test
    void resolve_throws_when_no_executor_is_registered_for_type() {
        ActionExecutorRegistry registry = new ActionExecutorRegistry(List.of(new StubExecutor(ActionType.SLACK)));

        assertThatThrownBy(() -> registry.resolve(ActionType.HTTP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP");
    }

    @Test
    void constructor_rejects_duplicate_executors_for_same_type() {
        StubExecutor first = new StubExecutor(ActionType.HTTP);
        StubExecutor second = new StubExecutor(ActionType.HTTP);

        assertThatThrownBy(() -> new ActionExecutorRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP");
    }

    private static final class StubExecutor implements ActionExecutor {
        private final ActionType supported;

        private StubExecutor(ActionType supported) {
            this.supported = supported;
        }

        @Override
        public boolean supports(ActionType type) {
            return type == supported;
        }

        @Override
        public ActionResult execute(Action action) {
            return ActionResult.ok("noop");
        }
    }
}
