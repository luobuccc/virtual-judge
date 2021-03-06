package judge.remote.provider.codeforces;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import judge.httpclient.DedicatedHttpClient;
import judge.httpclient.HttpStatusValidator;
import judge.httpclient.SimpleNameValueEntityFactory;
import judge.remote.RemoteOjInfo;
import judge.remote.account.RemoteAccount;
import judge.remote.provider.codeforces.CodeForcesTokenUtil.CodeForcesToken;
import judge.remote.querier.AuthenticatedQuerier;
import judge.remote.status.RemoteStatusType;
import judge.remote.status.SubmissionRemoteStatus;
import judge.remote.status.SubstringNormalizer;
import judge.remote.submitter.SubmissionInfo;

import org.apache.commons.lang3.Validate;
import org.apache.http.HttpEntity;
import org.springframework.stereotype.Component;

@Component
public class CodeForcesQuerier extends AuthenticatedQuerier {

    @Override
    public RemoteOjInfo getOjInfo() {
        return CodeForcesInfo.INFO;
    }

    @Override
    protected SubmissionRemoteStatus query(SubmissionInfo info, RemoteAccount remoteAccount, DedicatedHttpClient client) {
        String html = client.get("/contest/" + info.remoteProblemId.replaceAll("\\D.*", "") + "/submission/" + info.remoteRunId, HttpStatusValidator.SC_OK).getBody();

        String regex = 
                        "width:4em[\\s\\S]*?" +
                        "<td>" + info.remoteRunId + "</td>\\s*" + // run id
                        "<td>[\\s\\S]*?</td>\\s*" + // author
                        "<td>[\\s\\S]*?</td>\\s*" + // problem id
                        "<td>[\\s\\S]*?</td>\\s*" + // language
                        "<td>([\\s\\S]*?)</td>\\s*" + // status
                        "<td>([\\s\\S]*?)</td>\\s*" + // time
                        "<td>([\\s\\S]*?)</td>"; // memory
                        
        Matcher matcher = Pattern.compile(regex).matcher(html);
        Validate.isTrue(matcher.find());
        
        SubmissionRemoteStatus status = new SubmissionRemoteStatus();
        status.rawStatus = matcher.group(1).replaceAll("<.*?>", "").trim();
        status.executionTime = Integer.parseInt(matcher.group(2).replaceAll("\\D+", ""));
        status.executionMemory = Integer.parseInt(matcher.group(3).replaceAll("\\D+", ""));
        status.statusType = SubstringNormalizer.DEFAULT.getStatusType(status.rawStatus);
        if (status.statusType == RemoteStatusType.CE) {
            CodeForcesToken token = CodeForcesTokenUtil.getTokens(client);
            HttpEntity entity = SimpleNameValueEntityFactory.create( //
                    "submissionId", info.remoteRunId, //
                    "csrf_token", token.csrf_token //
            );
            html = client.post("/data/judgeProtocol", entity).getBody();
            status.compilationErrorInfo = "<pre>" + html.replaceAll("(\\\\r)?\\\\n", "\n").replaceAll("\\\\\\\\", "\\\\") + "</pre>";
        }
        
        matcher = Pattern.compile("on test\\s*(\\d+)").matcher(status.rawStatus);
        if (matcher.find()) {
            status.failCase  = Integer.parseInt(matcher.group(1));
        }

        return status;
    }
    
}
