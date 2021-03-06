package com.fuiou.mgr.http.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.fuiou.mer.model.TCustmrBusi;
import com.fuiou.mer.model.TIvrOrderInf;
import com.fuiou.mer.model.TWebLog;
import com.fuiou.mer.service.IvrOrderInfService;
import com.fuiou.mer.service.TCustmrBusiService;
import com.fuiou.mer.service.TWebLogService;
import com.fuiou.mer.util.BpsUtilBean;
import com.fuiou.mer.util.MemcacheUtil;
import com.fuiou.mer.util.TDataDictConst;
import com.fuiou.mgr.action.contract.CustmrBusiContractUtil;
import com.fuiou.mgr.bps.BpsTransaction;
import com.fuiou.mgr.util.CustmrBusiValidator;
import com.fuiou.mgr.util.VPCDecodeUtil;
import com.thoughtworks.xstream.core.util.Base64Encoder;

public class OrderPayWorker implements Runnable {
	
	private static final Logger logger = Logger.getLogger(OrderPayWorker.class);

	private IvrOrderInfService orderService = new IvrOrderInfService();
	private TCustmrBusiService custmrBusiService = new TCustmrBusiService();
	private TWebLogService webLogService = new TWebLogService();
	private Map<String, String> map = new HashMap<String, String>();

	Socket socket;

	public OrderPayWorker(Socket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		String str = null;
		String hangupCode = "DJ0051_DJ0052_DJ0053_DJ0054_DJ0055_DJ0056_DJ0057";//挂断应答码
		try {
			BufferedInputStream inputStream = new BufferedInputStream(socket.getInputStream());
			byte[] buffer = new byte[2048];
			int len = inputStream.read(buffer);
			str = new String(buffer, 0, len);
			logger.debug(str);
			if (StringUtils.isNotEmpty(str)) {
				parseMsg(str);
				TIvrOrderInf order = orderService.getOrder(map.get("OdrDtTm").substring(0, 8),map.get("OdrId"));
				logger.debug("from vpc msg reponse code:"+ map.get("RspCd"));
				if (map.get("RspCd") != null && "DJ0000".equals(map.get("RspCd"))) {
					doPay();
				}else if(map.get("RspCd") != null && hangupCode.contains(map.get("RspCd"))){//挂断
					if(null!=order){
						orderService.updateOrderSt(order.getROW_ID(), TDataDictConst.IVR_ORDER_PAY_OVER, 0);
					}
				}else{//其他错
					logger.debug("other error code:"+map.get("RspCd"));
				}
				//响应VPC
				responseVpc(order.getMOBILE_NO());
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void parseMsg(String respStr) {
		int index = respStr.indexOf("\r\n\r\n");
		respStr = respStr.substring(index);
		respStr = VPCDecodeUtil.doCrypt("D", respStr);
		logger.debug("respStr:"+respStr);
		String[] strs = respStr.split("&");
		for (String s : strs) {
			String[] ss = s.split("=");
			if ("CstmInfo".equals(ss[0])) {
				Base64Encoder base64Encoder = new Base64Encoder();
				byte[] b = base64Encoder.decode(ss[1]);
				String tempStr = new String(b);
				String pinStr = tempStr.substring(0, tempStr.length() - 1) + "|}";
				String pin = new String(base64Encoder.encode(pinStr.getBytes()));
				pin = pin.replaceAll("\r\n", "");
				pin = pin.replaceAll("\n", "");
				map.put("pin", pin);
			} else {
				map.put(s.split("=")[0], s.split("=")[1]);
			}
		}
	}

	private void doPay() throws Exception {
		TIvrOrderInf order = orderService.getOrder(map.get("OdrDtTm").substring(0, 8),map.get("OdrId"));
		TCustmrBusi custmrBusi = custmrBusiService.selectByAcntAndBusiCd(order.getMCHNT_CD(), TDataDictConst.BUSI_CD_INCOMEFOR,order.getACNT_NO(),CustmrBusiValidator.srcChnlMap.get(CustmrBusiValidator.SRC_CHNL_WEB));
		BpsUtilBean deductionResultBean = null,paymentResultBean = null;
		deductionResultBean = BpsTransaction.sendToUpmp(order,map.get("pin"));//扣款结果
		//扣款结果入库
		TWebLog webLogDBean = webLogService.saveWebLogD(order, deductionResultBean);//扣款结果入库
		if (webLogDBean == null){
			throw new Exception("insert order fail,order num is " + order.getORDER_NO());
		}else{
			logger.debug("weblog insert success");
		}
		if(BpsTransaction.SUCC_CODE.equals(deductionResultBean.getRespCode())){
			if("C".equals(order.getCONTRACT_FLAG())){//签约
				//根据支付结果修改协议库状态
				 boolean flag = CustmrBusiContractUtil.VERIFY_PASS.equals(custmrBusi.getACNT_IS_VERIFY_1());//户名证件号验证状态
				 custmrBusi.setACNT_IS_VERIFY_2(CustmrBusiContractUtil.VERIFY_PASS);//卡密验证通过
				 custmrBusi.setGROUP_ID(CustmrBusiValidator.srcChnlMap.get(CustmrBusiValidator.SRC_CHNL_IVR));//更改签约方式
				 String riskLevel = MemcacheUtil.getRiskLevel(custmrBusi.getMCHNT_CD());//低风险不需要卡密验证
				 //在卡密验证成功的情况下只需要判定户名卡号是否验证通过
				 if((CustmrBusiContractUtil.HIGH_RISK.equals(riskLevel)&&flag) || (CustmrBusiContractUtil.OTHER_RISK.equals(riskLevel)&&flag)){
					 custmrBusi.setCONTRACT_ST(CustmrBusiContractUtil.CONTRACT_ST_VALID);
				 }
				 int rows = custmrBusiService.updateByRowId(custmrBusi);//修改协议库
				 if(1!=rows){
					 throw new
					 	Exception("contracting....,custmr busi update fail,acnt_no="+custmrBusi.getACNT_NO());
				 }
			}else{
				custmrBusi.setREC_UPD_USR(custmrBusi.getUSER_NM());//持卡人自己
				custmrBusi.setREC_UPD_TS(new Date());
				custmrBusi.setRESERVED2("1");// 标示已解约
				custmrBusi.setCONTRACT_ST(CustmrBusiContractUtil.CONTRACT_ST_INVALID);//不生效
				custmrBusi.setACNT_IS_VERIFY_1(CustmrBusiContractUtil.VERIFY_UNPASS);
				custmrBusi.setACNT_IS_VERIFY_2(CustmrBusiContractUtil.VERIFY_UNPASS);
				custmrBusi.setACNT_IS_VERIFY_3(CustmrBusiContractUtil.VERIFY_UNPASS);
				custmrBusi.setACNT_IS_VERIFY_4(CustmrBusiContractUtil.VERIFY_UNPASS);
				int rows = custmrBusiService.updateByRowId(custmrBusi);
				 if(1!=rows){
					 throw new
					 	Exception("uncontracting....,custmr busi update fail,custmrbuis acnt_no="+custmrBusi.getACNT_NO());
				 }
			}
			 //如果扣款成功则立即发一笔付款
			paymentResultBean = BpsTransaction.cupsPayment(custmrBusi,order.getORDER_AMT()+"");
			//付款结果入库
			webLogService.saveWebLogC(webLogDBean,paymentResultBean);
			if(BpsTransaction.SUCC_CODE.equals(paymentResultBean.getRespCode())){//付款成功
				logger.debug("");
			}else{
				logger.debug("");
			}
		}
		if(BpsTransaction.SUCC_CODE.equals(deductionResultBean.getRespCode())){
			orderService.updateOrderSt(order.getROW_ID(),TDataDictConst.IVR_ORDER_PAY_SUCCESS,0);//修改订单状态为成功
		}else{
			if(order.getVERIFY_CNT()-1>0){
				orderService.updateOrderSt(order.getROW_ID(),TDataDictConst.IVR_ORDER_PAY_FAIL,order.getVERIFY_CNT()-1);//修改订单状态为失败
			}else{
				orderService.updateOrderSt(order.getROW_ID(),TDataDictConst.IVR_ORDER_PAY_FAIL,0);//修改订单状态为结束
			}
		}
	}

	private void responseVpc(String mobile) {
		StringBuilder params = new StringBuilder();
		params.append("Version=1.0.0&").append("TranTp=83&")
			  .append("TranSubTp=00&").append("OdrPhoneNum=" + mobile);
		String msg = "User-Agent: Donjin Http 0.1\r\nCache-Control: no-cache\r\nContent-Type: text/xml;charset=UTF-8\r\nAccept: */*\r\nContent-Length: " + params.length() + "\r\n\r\n" + params;
		try {
			socket.getOutputStream().write(msg.getBytes());
			socket.getOutputStream().flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
