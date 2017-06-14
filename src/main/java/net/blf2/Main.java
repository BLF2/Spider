package net.blf2;


import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by blf2 on 17-6-14.
 */
public class Main {
    private static String loginToken;
    private static CookieStore cookieStore;

    public static void main(String[] args) {
        requestLogin();
        List<String> problemIds = null;
        try {
            problemIds = getTestList();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        try {
            writeDataToFile(getProblemAndAnswer(problemIds));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean requestLogin() {
        try {
            System.out.println("开始登录");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet("http://etc.xxxx.edu.cn/meol/homepage/common/");

            HttpResponse response = httpClient.execute(httpGet);
            HttpEntity httpEntity = response.getEntity();
            if (response.getStatusLine().getStatusCode() == 200) {
                String responseContent = EntityUtils.toString(httpEntity);
                //   System.out.println(responseContent);
                String regxForSessionId = "<input type=\"hidden\" name=\"logintoken\" value=\"";
                Pattern patternForSessionId = Pattern.compile(regxForSessionId);
                Matcher matcherForSessionId = patternForSessionId.matcher(responseContent);
                if (matcherForSessionId.find()) {
                    loginToken = responseContent.substring(matcherForSessionId.end(), matcherForSessionId.end() + 13);
                    System.out.println(loginToken);
                    HttpPost httpPost = new HttpPost("http://etc.xxxx.edu.cn/meol/loginCheck.do");
                    List<NameValuePair> postNameValue = new LinkedList<NameValuePair>();
                    postNameValue.add(new BasicNameValuePair("logintoken", loginToken));
                    postNameValue.add(new BasicNameValuePair("IPT_LOGINUSERNAME", "xxxxxxxxxxx"));
                    postNameValue.add(new BasicNameValuePair("IPT_LOGINPASSWORD", "xxxxxxxxxxx"));
                    UrlEncodedFormEntity urlEncodedFormEntity = new UrlEncodedFormEntity(postNameValue, "UTF-8");
                    httpPost.setEntity(urlEncodedFormEntity);
                    HttpResponse httpResponse = httpClient.execute(httpPost);
                    if (httpResponse.getStatusLine().getStatusCode() == 302) {
                        Header[] headers = httpResponse.getHeaders("Location");
                        System.out.println(headers[0].getValue());
                        cookieStore = httpClient.getCookieStore();
                        if (headers.length > 0) {
                            URL url = new URL(headers[0].getValue());
                            URI uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(), null);
                            HttpGet httpGet1 = new HttpGet(uri);
                            DefaultHttpClient httpClient1 = new DefaultHttpClient();
                            httpClient1.setCookieStore(cookieStore);
                            HttpResponse httpResponse1 = httpClient1.execute(httpGet1);
                            if (httpResponse1.getStatusLine().getStatusCode() == 200) {
                                String returnContent = EntityUtils.toString(httpResponse1.getEntity());
                                //  System.out.println(returnContent);
                                if (returnContent.contains("任梦笛")) {
                                    System.out.println("log in success");
                                    return true;
                                }
                            }
                        }

                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    public static List<String> getTestList() throws Exception {
        String rUrl = "http://etc.xxxx.edu.cn/meol/common/question/questionbank/student/list.jsp?sortColumn=provideTime&tagbug=client&sortDirection=-1&perm=3840&strStyle=new06&cateId=29296&pagingPage=1&pagingNumberPer=500";
        URL url = new URL(rUrl);
        URI uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(), null);
        DefaultHttpClient defaultHttpClient = new DefaultHttpClient();
        defaultHttpClient.setCookieStore(cookieStore);
        HttpGet httpGetTestList = new HttpGet(uri);
        HttpResponse httpResponse = defaultHttpClient.execute(httpGetTestList);
        List<String> problemIds = new LinkedList<String>();
        if (httpResponse.getStatusLine().getStatusCode() == 200) {
            String returnContent = EntityUtils.toString(httpResponse.getEntity());
            //System.out.println(returnContent);
            String regPatten = "><a href=\"detail\\.jsp\\?id=[\\d]{6}\">";
            Pattern pattern = Pattern.compile(regPatten);
            Matcher matcher = pattern.matcher(returnContent);
            Pattern p = Pattern.compile("[\\d]{6}");
            while (matcher.find()) {
                Matcher m = p.matcher(matcher.group());
                if (m.find()) {
                    problemIds.add(m.group());
//                    System.out.println(m.group());
                }
            }
        }
        return problemIds.size() > 0 ? problemIds : null;
    }

    public static Map<String, List<String>> getProblemAndAnswer(List<String> problemIds) throws Exception {
        if (problemIds != null) {
            Map<String,List<String>>problemAnswerMap = new HashMap<String, List<String>>();
            for (String id : problemIds) {
                DefaultHttpClient defaultHttpClient = new DefaultHttpClient();
                defaultHttpClient.setCookieStore(cookieStore);
                URL url = new URL("http://etc.xxxx.edu.cn/meol/common/question/questionbank/student/detail.jsp?id=" + id);
                System.out.println("请求"+url);
                URI uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(), null);
                HttpGet httpGet = new HttpGet(uri);
                httpGet.setHeader("Connection", "close");
                HttpResponse httpResponse = defaultHttpClient.execute(httpGet);
                if (httpResponse.getStatusLine().getStatusCode() == 200) {
                    String pageSource = EntityUtils.toString(httpResponse.getEntity());
                    String key = "本题失败，题号是"+id;
                    String reg = "<input type='hidden' name='[\\d]{1,4}_content' id='[\\d]{1,4}_content' value='.*?'><iframe";
                    Pattern p = Pattern.compile(reg);
                    Matcher m = p.matcher(pageSource);
                    if(m.find()){
                        String[] strs = m.group().split("value='");
                        String strs2[] = strs[1].split("'><iframe");
                        key = strs2[0];
                    }
                    List<String>itemAndAnswer = new LinkedList<String>();
                    if (pageSource.contains("正确&nbsp;&nbsp;") && pageSource.contains("错误")) {
                        itemAndAnswer.addAll(Arrays.asList("正确","错误"));
                        String reg1 = "<input type=\"radio\" name=\"answer\" class=\"none\" .{0,7}>正确&nbsp;&nbsp; <input type=\"radio\" name=\"answer\" class=\"none\" .{0,7}>错误";
                        Pattern p1 = Pattern.compile(reg1);
                        Matcher m1 = p1.matcher(pageSource);
                        if(m1.find()) {
                            String strs3[] = m1.group().split("&nbsp;&nbsp;");
                            if(strs3[0].contains("checked")){
                                itemAndAnswer.add("本题为判断，答案是："+strs3[0].substring(strs3[0].length() - 2,strs3[0].length()));
                            }else{
                                itemAndAnswer.add("本题为判断，答案是："+strs3[1].substring(strs3[1].length() - 2,strs3[1].length()));
                            }
                        }

                    }else if(pageSource.contains("<th width=\"5%\"><input type=\"checkbox\" class=\"none\" name=\"answer\"")){
                        String reg1 = "<th width=\"5%\"><input type=\"checkbox\" class=\"none\" name=\"answer\"\\s\\s.{0,7}></th>\n\\s\\s\\s\\s\\s\\s\\s\\s<td><label>\n.*?\n\\s\\s\\s\\s\\s\\s\\s\\s</label></td>";
                        Pattern p1 = Pattern.compile(reg1);
                        Matcher m1 = p1.matcher(pageSource);
                        String answerForcb = "本题为多选，答案是：";
                        while(m1.find()){
                            String[] strs2 = m1.group().split("<th width=\"5%\"><input type=\"checkbox\" class=\"none\" name=\"answer\"\\s\\s.{0,7}></th>\n\\s\\s\\s\\s\\s\\s\\s\\s<td><label>\n");
                            String[] strs3 = strs2[1].split("\n\\s\\s\\s\\s\\s\\s\\s\\s</label></td>");
                            itemAndAnswer.add(strs3[0].trim());
                            if(m1.group().contains("checked")){
                                answerForcb += strs3[0].trim()+" ";
                            }
                        }
                        itemAndAnswer.add(answerForcb);
                    }else if(pageSource.contains("<input type=\"radio\" class=\"none\" name=\"answer\"") && !pageSource.contains("正确&nbsp;&nbsp;") && !pageSource.contains("\"错误") ){
                        String reg1 = "<th width=\"5%\"><input type=\"radio\" class=\"none\" name=\"answer\" .{0,7}></th>\n\\s\\s\\s\\s\\s\\s\\s\\s<td><label>\n\\s\\s\\s\\s\\s\\s\\s\\s\\s\\s\\s\\s.*?\n\\s\\s\\s\\s\\s\\s\\s\\s</label></td>";
                        Pattern p1 = Pattern.compile(reg1);
                        Matcher m1 = p1.matcher(pageSource);
                        String answerForcb = "本题为单选，答案是：";
                        while (m1.find()){
                            String[] strs2 = m1.group().split("<th width=\"5%\"><input type=\"radio\" class=\"none\" name=\"answer\" .{0,7}></th>\n\\s\\s\\s\\s\\s\\s\\s\\s<td><label>\n");
                            String[] strs3 = strs2[1].split("\n\\s\\s\\s\\s\\s\\s\\s\\s</label></td>");
                            itemAndAnswer.add(strs3[0].trim());
                            if(m1.group().contains("checked")){
                                answerForcb += strs3[0].trim() + " ";
                            }
                        }
                        itemAndAnswer.add(answerForcb);
                    }
                    problemAnswerMap.put(key,itemAndAnswer);
                }else{
                    System.out.println("请求未成功，ID："+id);
                }
            }
            return problemAnswerMap;
        }
        return null;
    }
    public static void writeDataToFile(Map<String,List<String>>map) throws FileNotFoundException {
        String fileName = System.getProperties().getProperty("user.dir")+"/result.txt";
        File file = new File(fileName);
        PrintStream ps = new PrintStream(new FileOutputStream(file));
        int i = 1;
        for(Map.Entry<String,List<String>> entry : map.entrySet()){
            List<String> values = entry.getValue();
            ps.println(i+"."+entry.getKey());
            for(String str : values){
                ps.println(str);
            }
            ps.print("\n\n");
            i++;
        }
        ps.close();
    }
}
