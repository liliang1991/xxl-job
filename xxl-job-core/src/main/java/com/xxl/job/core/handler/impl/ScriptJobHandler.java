package com.xxl.job.core.handler.impl;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.log.XxlJobLogger;
import com.xxl.job.core.util.ScriptUtil;

import javax.annotation.Resource;
import java.io.File;

/**
 * Created by xuxueli on 17/4/27.
 */
public class ScriptJobHandler extends IJobHandler {

    private int jobId;
    private long glueUpdatetime;
    private String gluesource;
    private GlueTypeEnum glueType;

    public ScriptJobHandler(int jobId, long glueUpdatetime, String gluesource, GlueTypeEnum glueType) {
        this.jobId = jobId;
        this.glueUpdatetime = glueUpdatetime;
        this.gluesource = gluesource;
        this.glueType = glueType;

        // clean old script file
        File glueSrcPath = new File(XxlJobFileAppender.getGlueSrcPath());
        if (glueSrcPath.exists()) {
            File[] glueSrcFileList = glueSrcPath.listFiles();
            if (glueSrcFileList != null && glueSrcFileList.length > 0) {
                for (File glueSrcFileItem : glueSrcFileList) {
                    if (glueSrcFileItem.getName().startsWith(String.valueOf(jobId) + "_")) {
                        glueSrcFileItem.delete();
                    }
                }
            }
        }

    }

    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    @Override
    public ReturnT<String> execute(String param) throws Exception {

        if (!glueType.isScript()) {
            return new ReturnT<String>(IJobHandler.FAIL.getCode(), "glueType[" + glueType + "] invalid.");
        }
        if (param == null) {
            return new ReturnT<String>(IJobHandler.FAIL.getCode(), "执行参数不能为空");

        }

        // cmd
        String cmd = glueType.getCmd();

        // make script file
        String scriptFileName = XxlJobFileAppender.getGlueSrcPath()
                .concat(File.separator)
                .concat(String.valueOf(jobId))
                .concat("_")
                .concat(String.valueOf(glueUpdatetime))
                .concat(glueType.getSuffix());
        File scriptFile = new File(scriptFileName);
        if (!scriptFile.exists()) {
            ScriptUtil.markScriptFile(scriptFileName, gluesource);
        }

        // log file
        String logFileName = XxlJobContext.getXxlJobContext().getJobLogFileName();
        int exitValue=chmodScriptFile("chmod 755 "+scriptFileName);
        if(exitValue!=0){
            return new ReturnT<String>(IJobHandler.FAIL.getCode(), "chmod script fail======,exitValue(" + exitValue + ") is failed");

        }
        // script params：0=param、1=分片序号、2=分片总数
        //修改代码使其可以传递多个参数
        String[] params = param.split(",");
        String userScript="su - "+params[params.length-1]+" -c ";
        //scriptFileName 拼接后的格式 su escheduler -c "/home/escheduler/data/1.sh 1 2"
        scriptFileName=userScript+"\""+scriptFileName;
        //参数中删除执行用户
        params=delAnyPosition(params,params.length-1);

        String[] scriptParams = new String[params.length + 2];
        for (int i = 0; i < params.length; i++) {
            scriptParams[i] = params[i];
        }
        //分片序号和分片总数
        scriptParams[params.length] = String.valueOf(XxlJobContext.getXxlJobContext().getShardIndex());
        scriptParams[params.length + 1] = String.valueOf(XxlJobContext.getXxlJobContext().getShardTotal());
//        scriptParams[0] = param;
//        scriptParams[1] = String.valueOf(XxlJobContext.getXxlJobContext().getShardIndex());
//        scriptParams[2] = String.valueOf(XxlJobContext.getXxlJobContext().getShardTotal());

        // invoke
        XxlJobLogger.log("----------- script file:" + scriptFileName + " -----------");
        exitValue = ScriptUtil.execToFile(cmd, scriptFileName, logFileName, scriptParams);

        if (exitValue == 0) {
            return IJobHandler.SUCCESS;
        } else {
            return new ReturnT<String>(IJobHandler.FAIL.getCode(), "script exit value(" + exitValue + ") is failed");
        }

    }
    public static int chmodScriptFile(String cmd)throws Exception{
        String[] commands = { "bash", "-c", cmd };

        final Process process = Runtime.getRuntime().exec(commands);
        return process.waitFor();

    }
    public static String[] delAnyPosition(String[] arr, int position) {
        //判断是否合法
        if (position >= arr.length || position < 0) {
            return null;
        }
        String[] res = new String[arr.length - 1];
        for (int i = 0; i < res.length; i++) {
            if (i < position) {
                res[i] = arr[i];
            } else {
                res[i] = arr[i + 1];
            }
        }
        return res;
    }
}
