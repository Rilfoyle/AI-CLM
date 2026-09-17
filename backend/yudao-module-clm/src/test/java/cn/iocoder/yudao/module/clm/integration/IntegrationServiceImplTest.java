package cn.iocoder.yudao.module.clm.integration;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceImplTest {

    @InjectMocks private IntegrationServiceImpl service;
    @Mock private IntegrationRunMapper runMapper;
    @Mock private IntegrationDeliveryMapper deliveryMapper;
    @Mock private ClmAuditService auditService;

    @Test
    void enqueue_isIdempotentAndNeverPretendsExternalSuccess() {
        when(deliveryMapper.selectByKey("todo-1")).thenReturn(null);
        doAnswer(invocation -> {
            IntegrationDeliveryDO row = invocation.getArgument(0);
            row.setId(8L);
            return 1;
        }).when(deliveryMapper).insert(any(IntegrationDeliveryDO.class));

        Long id = service.enqueue("todo-1", "TODO", 2L, 3L, "task-1", "/clm/approval?taskId=task-1");

        assertEquals(8L, id);
        ArgumentCaptor<IntegrationDeliveryDO> captor = ArgumentCaptor.forClass(IntegrationDeliveryDO.class);
        verify(deliveryMapper).insert(captor.capture());
        assertEquals("NOT_CONFIGURED", captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getAttemptCount());
    }

    @Test
    void cancelByTaskId_cancelsEveryPendingProjection() {
        when(deliveryMapper.selectList(
                org.mockito.ArgumentMatchers.<SFunction<IntegrationDeliveryDO, ?>>any(), eq("task-2")))
                .thenReturn(List.of(new IntegrationDeliveryDO().setId(1L).setStatus("NOT_CONFIGURED"),
                        new IntegrationDeliveryDO().setId(2L).setStatus("CANCELED")));

        service.cancelByTaskId("task-2", "任务已转交");

        verify(deliveryMapper).updateById(argThat((IntegrationDeliveryDO row) ->
                Long.valueOf(1L).equals(row.getId()) && "CANCELED".equals(row.getStatus())
                        && "任务已转交".equals(row.getLastError())));
        verify(deliveryMapper, times(1)).updateById(any(IntegrationDeliveryDO.class));
    }
}
