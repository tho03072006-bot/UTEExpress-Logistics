# Kết quả full test đợt 2

Nhánh `feature_Tho_lien_tinh`, code tại `00728df`. Lần chạy hoàn tất lúc **22:33:52 ngày 23/10/2026 (+07:00)**.

Chuẩn bị simulator: `mvn -B -Pmomo-simulator '-Dmaven.test.skip=true' package` thành công. Sau đó `mvn -B test` thành công, thời gian Maven báo 2 phút 26 giây.

**102 suite; 1119 test; 0 failure; 0 error; 0 skipped.** Đã đối chiếu tổng XML với dòng tổng kết của cùng lần chạy Maven.

Bảng lấy từ thuộc tính tổng kết của `target/surefire-reports/TEST-*.xml`; không đưa system properties, log cấu hình hay dữ liệu đăng nhập vào tài liệu. Tên suite bỏ tiền tố chung `vn.edu.hcmute.uteexpress.` cho dễ đọc. Các lần chạy riêng trước đó không cộng vào tổng này.

| Suite | Test | Failure | Error | Skipped |
|---|---:|---:|---:|---:|
| config.AivenOnlyConfigTest | 4 | 0 | 0 | 0 |
| config.CloudMultipartConfigTest | 1 | 0 | 0 | 0 |
| config.SessionTimeoutConfigTest | 6 | 0 | 0 | 0 |
| config.TrackedSecretsTest | 2 | 0 | 0 | 0 |
| controller.admin.StaffUploadExceptionHandlerTest | 3 | 0 | 0 | 0 |
| controller.manager.ManagerReportCsvTest | 2 | 0 | 0 | 0 |
| dto.admin.AdminAccountValidationTest | 51 | 0 | 0 | 0 |
| dto.admin.StaffAccountValidationTest | 21 | 0 | 0 | 0 |
| dto.manager.ControlsValidationTest | 37 | 0 | 0 | 0 |
| dto.manager.OperationsValidationTest | 16 | 0 | 0 | 0 |
| exception.UploadExceptionHandlerTest | 1 | 0 | 0 | 0 |
| payment.AivenMomoConnectionIntegrationTest | 1 | 0 | 0 | 0 |
| payment.AivenMomoPackagedSmokeIntegrationTest | 1 | 0 | 0 | 0 |
| payment.MomoHealthClientTest | 5 | 0 | 0 | 0 |
| payment.MomoLocalWebIntegrationTest | 6 | 0 | 0 | 0 |
| payment.MomoSimulatorIntegrationTest | 90 | 0 | 0 | 0 |
| payment.MomoStartupServiceTest | 14 | 0 | 0 | 0 |
| payment.MomoTicketCodecTest | 7 | 0 | 0 | 0 |
| profile.AccountProfileIntegrationTest | 8 | 0 | 0 | 0 |
| repository.admin.StaffQueryValidationTest | 1 | 0 | 0 | 0 |
| security.admin.PublicAccountSessionFilterTest | 7 | 0 | 0 | 0 |
| security.admin.StaffApiSecurityIntegrationTest | 10 | 0 | 0 | 0 |
| security.admin.StaffLoginAttemptServiceTest | 2 | 0 | 0 | 0 |
| security.admin.StaffSecurityIntegrationTest | 56 | 0 | 0 | 0 |
| security.admin.StaffSessionTimeoutTest | 7 | 0 | 0 | 0 |
| security.JwtTokenProviderTest | 42 | 0 | 0 | 0 |
| security.LoginFailureCountIntegrationTest | 3 | 0 | 0 | 0 |
| security.LoginOutsideTransactionIntegrationTest | 1 | 0 | 0 | 0 |
| security.PublicAuthRateGuardTest | 3 | 0 | 0 | 0 |
| security.UserDetailsServiceImplTest | 5 | 0 | 0 | 0 |
| service.admin.AivenAccountIntegrationTest | 6 | 0 | 0 | 0 |
| service.admin.impl.AdminAccountServiceImplTest | 30 | 0 | 0 | 0 |
| service.admin.impl.AuditServiceImplTest | 16 | 0 | 0 | 0 |
| service.admin.impl.ShippingTariffServiceImplTest | 10 | 0 | 0 | 0 |
| service.admin.impl.StaffAccountServiceImplTest | 24 | 0 | 0 | 0 |
| service.admin.impl.StaffApiAccessServiceImplTest | 16 | 0 | 0 | 0 |
| service.assistant.AssistantIntegrationTest | 27 | 0 | 0 | 0 |
| service.assistant.AssistantRateGuardTest | 2 | 0 | 0 | 0 |
| service.assistant.impl.AssistantChatServiceImplTest | 12 | 0 | 0 | 0 |
| service.assistant.impl.GroqAssistantAiGatewayImplTest | 16 | 0 | 0 | 0 |
| service.FeeEstimateServiceTest | 2 | 0 | 0 | 0 |
| service.impl.AuthServiceImplTest | 9 | 0 | 0 | 0 |
| service.impl.OrderFeeEstimateTest | 4 | 0 | 0 | 0 |
| service.impl.OrderPaymentGuardTest | 5 | 0 | 0 | 0 |
| service.impl.ServiceReviewServiceImplTest | 4 | 0 | 0 | 0 |
| service.manager.AivenControlsIntegrationTest | 2 | 0 | 0 | 0 |
| service.manager.AivenOperationsIntegrationTest | 2 | 0 | 0 | 0 |
| service.manager.impl.ManagerDashboardServiceImplTest | 1 | 0 | 0 | 0 |
| service.manager.impl.ManagerOperationsServiceImplTest | 15 | 0 | 0 | 0 |
| service.manager.impl.ManagerOrderServiceImplTest | 7 | 0 | 0 | 0 |
| service.manager.impl.StaffDashboardChartServiceImplTest | 12 | 0 | 0 | 0 |
| service.manager.impl.StaffWorkbenchServiceImplTest | 21 | 0 | 0 | 0 |
| service.media.CloudMediaStorageServiceTest | 15 | 0 | 0 | 0 |
| service.media.impl.BoundedBodySubscriberTest | 2 | 0 | 0 | 0 |
| service.media.impl.ReviewMediaServiceImplTest | 3 | 0 | 0 | 0 |
| service.routing.BranchAdminServiceTest | 7 | 0 | 0 | 0 |
| service.routing.HubServiceTest | 7 | 0 | 0 | 0 |
| service.routing.RouteQuoteServiceTest | 25 | 0 | 0 | 0 |
| service.routing.RouteTariffAdminServiceTest | 6 | 0 | 0 | 0 |
| service.routing.StaffBranchServiceTest | 15 | 0 | 0 | 0 |
| service.tracking.impl.DeliveryProofServiceImplTest | 6 | 0 | 0 | 0 |
| service.tracking.impl.DeliveryProofStorageServiceImplTest | 6 | 0 | 0 | 0 |
| service.tracking.impl.OrderStatusWebSocketListenerTest | 1 | 0 | 0 | 0 |
| service.tracking.impl.SenderOrderMailListenerTest | 5 | 0 | 0 | 0 |
| service.tracking.impl.ShipperStatisticsServiceImplTest | 6 | 0 | 0 | 0 |
| service.tracking.impl.TrackingServiceImplTest | 18 | 0 | 0 | 0 |
| service.tracking.ShipperMapsTest | 2 | 0 | 0 | 0 |
| service.tracking.ShipperRouteOrderingTest | 6 | 0 | 0 | 0 |
| tool.admin.ProvisionInitialAdminTest | 3 | 0 | 0 | 0 |
| tool.admin.RecoverIssuedStaffAccountsTest | 10 | 0 | 0 | 0 |
| tool.database.AivenDatabaseToolsTest | 15 | 0 | 0 | 0 |
| tool.database.BranchSchemaSqlTest | 10 | 0 | 0 | 0 |
| tool.database.BranchSeedDataTest | 5 | 0 | 0 | 0 |
| tool.database.MigrateAivenCloudFilesTest | 4 | 0 | 0 | 0 |
| tool.database.UpgradeAivenAccountsTest | 6 | 0 | 0 | 0 |
| tool.database.UpgradeAivenAuthValidationTest | 3 | 0 | 0 | 0 |
| tool.database.UpgradeAivenBranchesTest | 26 | 0 | 0 | 0 |
| tool.database.UpgradeAivenCloudFilesTest | 4 | 0 | 0 | 0 |
| tool.database.UpgradeAivenControlsTest | 5 | 0 | 0 | 0 |
| tool.database.UpgradeAivenDropOffTest | 7 | 0 | 0 | 0 |
| tool.database.UpgradeAivenLegacyRoutesTest | 7 | 0 | 0 | 0 |
| tool.database.UpgradeAivenOrderSubmissionsTest | 4 | 0 | 0 | 0 |
| tool.database.UpgradeAivenReturnLegTest | 4 | 0 | 0 | 0 |
| tool.database.UpgradeAivenShipperTest | 2 | 0 | 0 | 0 |
| tracking.CrossRoleSynchronizationIntegrationTest | 22 | 0 | 0 | 0 |
| tracking.DispatchRulesIntegrationTest | 4 | 0 | 0 | 0 |
| tracking.DropOffIntegrationTest | 5 | 0 | 0 | 0 |
| tracking.InterprovincialReturnIntegrationTest | 4 | 0 | 0 | 0 |
| tracking.LogisticsIntegrationTest | 31 | 0 | 0 | 0 |
| tracking.NotificationWebSocketTest | 4 | 0 | 0 | 0 |
| tracking.OperationsControlsIntegrationTest | 30 | 0 | 0 | 0 |
| tracking.PaymentConcurrencyIntegrationTest | 2 | 0 | 0 | 0 |
| tracking.PickupStatisticsIntegrationTest | 2 | 0 | 0 | 0 |
| tracking.ShipperOperationsIntegrationTest | 18 | 0 | 0 | 0 |
| tracking.StaffWorkspaceIntegrationTest | 8 | 0 | 0 | 0 |
| UteexpressApplicationTests | 3 | 0 | 0 | 0 |
| util.DeliverySuccessRateTest | 7 | 0 | 0 | 0 |
| util.MailFromNameSetterTest | 4 | 0 | 0 | 0 |
| util.PaymentReadinessTest | 13 | 0 | 0 | 0 |
| validation.FormValidationIntegrationTest | 4 | 0 | 0 | 0 |
| validation.InputFuzzIntegrationTest | 1 | 0 | 0 | 0 |
| validation.PublicValidationIntegrationTest | 56 | 0 | 0 | 0 |

Một số ca kiểm đầu vào trùng, từ chối quyền và lỗi SMTP giả lập có thể ghi WARN/ERROR trong log theo thiết kế. Kết luận pass/fail dựa trên assertion và kết quả Surefire, không dựa riêng vào việc có từ ERROR trong log.

Chi tiết phạm vi, bằng chứng Aiven/web và các việc còn lại: xem `BAO_CAO_KIEM_THU_DOT_2.md`.
