package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.TemplateResponse;
import java.util.List;

public interface ListTemplatesUseCase {

  List<TemplateResponse> list();
}
