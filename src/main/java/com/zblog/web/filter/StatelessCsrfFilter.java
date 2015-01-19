package com.zblog.web.filter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import com.zblog.common.util.Base64Codec;
import com.zblog.common.util.CookieUtil;
import com.zblog.common.util.ServletUtils;
import com.zblog.common.util.StringUtils;
import com.zblog.common.util.constants.Constants;

/**
 * 处理csrf攻击,Stateless CSRF方案
 * 
 * @author zhou
 * 
 */
public class StatelessCsrfFilter extends OncePerRequestFilter{
  private List<String> excludes = new ArrayList<>();
  static List<String> METHODS = Arrays.asList("POST", "DELETE", "PUT", "PATCH");
  private Random random = new SecureRandom();

  @Override
  protected void initFilterBean() throws ServletException{
    FilterConfig config = getFilterConfig();
    excludes.add(config.getInitParameter("exclude"));
  }

  private boolean isAjaxVerificationToken(HttpServletRequest request, String csrfToken){
    String headToken = request.getHeader(Constants.CSRF_TOKEN);
    /* 此处要base64解码 */
    if(!StringUtils.isBlank(headToken))
      headToken = new String(Base64Codec.decode(headToken));

    return headToken != null && headToken.equals(csrfToken);
  }

  /**
   * 校验非ajax提交
   * <p>
   * 由于有可能为multipart提交所以须在该filter前做parseMultipart,否则request.getParameter会获取不到值
   * </p>
   * 
   * @param request
   * @param csrfToken
   * @return
   */
  private boolean isVerificationToken(HttpServletRequest request, String csrfToken){
    String paramToken = request.getParameter(Constants.CSRF_TOKEN);

    if(StringUtils.isBlank(paramToken))
      return false;
    
    /* 当flash文件post上传时，可能crsf的为cookie中值,就需要base64解码*/
    return paramToken.equals(csrfToken)
        || (ServletUtils.isMultipartContent(request) && new String(Base64Codec.decode(paramToken)).equals(csrfToken));
  }

  private boolean isAjax(HttpServletRequest request){
    return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException{
    String url = request.getRequestURI();
    for(String match : excludes){
      if(url.matches(match)){
        filterChain.doFilter(request, response);
        return;
      }
    }

    CookieUtil cookieUtil = new CookieUtil(request, response);
    /* 页面值均为base64编码后的值 */
    String csrfToken = cookieUtil.getCookie(Constants.COOKIE_CSRF_TOKEN);

    if(METHODS.contains(request.getMethod())){
      boolean ajax = isAjax(request);
      if(ajax && !isAjaxVerificationToken(request, csrfToken)){
        response.setContentType("application/json");
        response.setCharacterEncoding(Constants.ENCODING_UTF_8);
        response.getWriter().write("{'status':'403','success':false,'msg':'非法请求,请刷新重试'}");
        return;
      }else if(!ajax && !isVerificationToken(request, csrfToken)){
        if(response.isCommitted())
          response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
    }

    csrfToken = Long.toString(random.nextLong(), 36);
    cookieUtil.setCookie(Constants.COOKIE_CSRF_TOKEN, csrfToken, false, 1800);
    request.setAttribute(Constants.CSRF_TOKEN, csrfToken);

    filterChain.doFilter(request, response);
  }

}