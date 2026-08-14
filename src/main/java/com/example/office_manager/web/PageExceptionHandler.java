package com.example.office_manager.web;

import com.example.office_manager.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = PageController.class)
public class PageExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PageExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ModelAndView business(BusinessException exception) {
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(exception.getStatus());
        modelAndView.addObject("status", exception.getStatus().value());
        modelAndView.addObject("error", exception.getStatus().getReasonPhrase());
        modelAndView.addObject("message", exception.getMessage());
        return modelAndView;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView accessDenied() {
        ModelAndView modelAndView = new ModelAndView("access-denied");
        modelAndView.setStatus(HttpStatus.FORBIDDEN);
        return modelAndView;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception exception) {
        log.error("화면 처리 중 예상하지 못한 오류가 발생했습니다.", exception);
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        modelAndView.addObject("status", 500);
        modelAndView.addObject("error", "Internal Server Error");
        modelAndView.addObject("message", "화면을 불러오는 중 오류가 발생했습니다.");
        return modelAndView;
    }
}
