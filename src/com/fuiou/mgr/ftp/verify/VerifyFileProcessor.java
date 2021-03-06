package com.fuiou.mgr.ftp.verify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fuiou.mer.util.TDataDictConst;
import com.fuiou.mgr.ftp.FileProcessor;

/**
 * Richard Xiong
 * 实名验证文件处理对象，用于校验，处理和入库文件信息和文件明细信息
 */
public class VerifyFileProcessor extends FileProcessor {
	private static final Logger logger = LoggerFactory.getLogger(VerifyFileProcessor.class);
	protected String busiCd = TDataDictConst.BUSI_CD_VERIFY;
	@Override
	public void setBusiCd() {
		super.busiCd = this.busiCd;
	}
}
