ALTER TABLE audit_logs
DROP CONSTRAINT chk_audit_action;

ALTER TABLE audit_logs
    ADD CONSTRAINT chk_audit_action
        CHECK (
    action IN (
    'USER_LOGIN',
    'USER_LOGOUT',
    'TOKEN_REFRESH',
    'USER_REGISTERED',
    'TASK_CREATED',
    'TASK_UPDATED',
    'TASK_DELETED'
    )
    );

ALTER TABLE audit_logs
DROP CONSTRAINT chk_audit_resource_type;

ALTER TABLE audit_logs
    ADD CONSTRAINT chk_audit_resource_type
        CHECK (
            resource_type IS NULL
                OR resource_type IN (
                                     'TASK',
                                     'SESSION',
                                     'USER'
                )
            );