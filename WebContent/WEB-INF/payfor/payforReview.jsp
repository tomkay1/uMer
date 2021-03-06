<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%@page import="com.fuiou.mer.util.TDataDictConst"%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib prefix="sx" uri="/struts-dojo-tags" %>
<%@ include file="/common.jsp" %>
<%@ page import="java.util.Date" %>
<%@page import="java.text.SimpleDateFormat" %>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<title>付款|复核付款交易</title>
<sx:head/>
<script language="javascript" src="<%=root %>/js/SearchMerchant.js"></script>
<script language="javascript" type="text/javascript" src="<%=root %>/My97DatePicker/WdatePicker.js"></script>
<script language="javascript" src="<%=root%>/styles/iframe.js" type="text/javascript"></script>
<link href="<%=root %>/styles/style_up.css" rel="stylesheet" type="text/css" />
<%
	Date dt = new Date();
	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
	String dateTimeStr = sdf.format(dt);
	String fileBusiTp = TDataDictConst.BUSI_CD_PAYFOR;
	request.setAttribute("fileBusiTp",fileBusiTp);
%>

<script language="javascript">
function valiFileName() {
    for( var i=0 ; i<document.getElementById("fileName").value.length ; i ++ ){
		var c = document.getElementById("fileName").value.charAt(i) ;
		if(c == '%'){
			document.getElementById("fileName_er").innerHTML="文件名中不能包含百分号%!";//innerHTML
			document.getElementById("fileName").focus(); 
			document.getElementById("fileName").select();
			return false;
		}else{
			document.getElementById("fileName_er").innerHTML="";//innerHTML
		}
	}
	document.forms[0].submit();
}


function check(){
	var busicd = document.getElementById("stateSel").value;
	if(busicd == 'single'){
		document.getElementById("fileName").disabled=true; 
	}else{
		document.getElementById("fileName").disabled=false;
	}
}
</script>
</head>
<body onload="resizeAll();" style="background-color: #e8eef7; padding: 0px; margin: 0px;">
<div id="condition">
<s:form name="queryForm" method="post" id="statDay" action="/payfor/PayforReviewAction_execute.action" target="data_eara_frame_down" theme="simple">
	<table >
		<s:fielderror></s:fielderror>
		<tr>
			<td align="right">开始时间：</td>
			<td><input type="text" class="Wdate" name="startDate" id="startDate" value="<%=dateTimeStr %>" onFocus="WdatePicker({dateFmt:'yyyyMMdd'})"/></td>
			<td align="right">结束时间：</td>
			<td><input type="text" class="Wdate" name="endDate" id="endDate" value="<%=dateTimeStr %>" onFocus="WdatePicker({dateFmt:'yyyyMMdd'})"/> </td>
		</tr>
		<tr>
			<td align="right">复核内容：</td>
			<td><select name="stateSel" id="stateSel"  onchange="check()">
				<option value="file">批量上传文件</option>
				<option value="single">单笔付款交易</option>
			</select></td>
			<td align="right">文件名：</td>
			<td><input type="text" name="fileName" id="fileName" /></td>
			<td align="right"><input type="button" name="查询" value="查询" class="type_button" onClick="valiFileName();"/>&nbsp;&nbsp;<font id="fileName_er"></font></td>
		</tr>
	</table>
	<s:hidden name="fileBusiTp" id="fileBusiTp" value="%{#request.fileBusiTp}"></s:hidden>
</s:form>
</div>
</body>
</html>