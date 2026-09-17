package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import cn.iocoder.yudao.module.system.api.sms.dto.send.SmsSendSingleToUserReqDTO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class BpmMessageServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmMessageServiceImpl messageService;

    @Mock
    private SmsSendApi smsSendApi;
    @Mock
    private WebProperties webProperties;

    @Test
    public void testSmsDisabledDoesNotRequireSampleConfiguration() {
        ReflectionTestUtils.setField(messageService, "smsEnabled", false);

        assertDoesNotThrow(() -> messageService.sendMessageWhenProcessInstanceApprove(approveRequest()));

        verifyNoInteractions(smsSendApi, webProperties);
    }

    @Test
    public void testSmsFailureDoesNotInterruptWorkflow() {
        ReflectionTestUtils.setField(messageService, "smsEnabled", true);
        WebProperties.Ui adminUi = new WebProperties.Ui();
        adminUi.setUrl("http://127.0.0.1:3000");
        when(webProperties.getAdminUi()).thenReturn(adminUi);
        when(smsSendApi.sendSingleSmsToAdmin(any(SmsSendSingleToUserReqDTO.class)))
                .thenThrow(new IllegalStateException("SMS integration is unavailable"));

        assertDoesNotThrow(() -> messageService.sendMessageWhenProcessInstanceApprove(approveRequest()));

        verify(smsSendApi).sendSingleSmsToAdmin(any(SmsSendSingleToUserReqDTO.class));
    }

    private static BpmMessageSendWhenProcessInstanceApproveReqDTO approveRequest() {
        BpmMessageSendWhenProcessInstanceApproveReqDTO request =
                new BpmMessageSendWhenProcessInstanceApproveReqDTO();
        request.setProcessInstanceId("process-instance-1");
        request.setProcessInstanceName("TuriX contract approval");
        request.setStartUserId(200001L);
        return request;
    }

}
