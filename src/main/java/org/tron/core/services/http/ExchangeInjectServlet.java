package org.tron.core.services.http;

import java.io.IOException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.core.Wallet;
import org.tron.protos.Contract.ExchangeInjectContract;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;

import static org.tron.core.services.http.Util.getVisible;
import static org.tron.core.services.http.Util.getVisiblePost;
import static org.tron.core.services.http.Util.setTransactionPermissionId;


@Component
@Slf4j(topic = "API")
public class ExchangeInjectServlet extends HttpServlet {

  @Autowired
  private Wallet wallet;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String contract = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      boolean visible = getVisiblePost( contract );
      ExchangeInjectContract.Builder build = ExchangeInjectContract.newBuilder();
      JsonFormat.merge(contract, build, visible );
      Transaction tx = wallet
          .createTransactionCapsule(build.build(), ContractType.ExchangeInjectContract)
          .getInstance();
      JSONObject jsonObject = JSONObject.parseObject(contract);
      tx = setTransactionPermissionId(jsonObject, tx);
      response.getWriter().println(Util.printCreateTransaction(tx, visible ));
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}
