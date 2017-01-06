package mario.main;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Administrator on 2017/1/4.
 */
public class NetEaseCrawler {

    public static final String BASE_URL = "http://music.163.com/";
    public static final String RECORD_URL = "http://music.163.com/weapi/v1/play/record?csrf_token=";
    public static final String CONTENT_URL = "http://music.163.com/weapi/v1/resource/comments/R_SO_4_%s/?csrf_token=";

    private static List<String> songIdList = new ArrayList<String>();
    private static int offset = 0;//步长第一次从0开始，第二次从20开始

    private String aesEncrypt(String value, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes("UTF-8"), "AES"), new IvParameterSpec(
                "0102030405060708".getBytes("UTF-8")));
        return Base64.encodeBase64String(cipher.doFinal(value.getBytes()));
    }

    public static final String uid = "103352648";
    public static final String text = "{\"username\": \"\", \"rememberLogin\": \"true\", \"password\": \"\",\"type\":\"0\",\"uid\":\"" + uid + "\",\"offset\":\"%s\"}";
    public static final String replytext = "{\"username\": \"\", \"rememberLogin\": \"true\", \"password\": \"\",\"offset\":\"%s\"}";

    public static List<UserComment> userCommentList;

    public static void main(String[] args) throws Exception {
        NetEaseCrawler ut = new NetEaseCrawler();
        // System.out.println(ut.getCommentCount("http://music.163.com/song?id=5179544"));
        ut.getUserRecardMusicList();//获取听过的歌曲
        //songIdList.add("247534");
        int songSize = 1;

        if (CollectionUtils.isNotEmpty(songIdList)) {
            System.err.println("获取到该用户[" + uid + "]的歌曲总数为：" + songIdList.size());
            for (String id : songIdList) {
                System.err.println("开始第" + songSize + "首歌曲的遍历，用于匹配评论，总共会匹配前100条。");
                for (int pageIndex = 0; pageIndex < 10; pageIndex++) {
                    Thread.sleep(5000);
                    System.err.println("开始第" + pageIndex + "页数据,offset=" + offset + "...");
                    ut.getCommentCount("http://music.163.com/song?id=" + id, pageIndex);//循环拿每一个歌曲的留言信息
                    offset = offset + 10;
                }
                offset = 0;
                songSize++;
            }
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("######################所有数据执行完毕！！！");
                if (userCommentList != null)
                    System.err.println("数量：" + userCommentList.size() + ",抓取到的评论信息：" + JSON.toJSONString(userCommentList));
            }
        });
    }

    public void getCommentCount(String baesURL, int pageIndex) throws Exception {
        String id = baesURL.substring(baesURL.indexOf("=") + 1, baesURL.length());
        String secKey = getSecKey();
        String encText = getAesEncrypt2(secKey);
        String encSecKey = rsaEncrypt(secKey);
        Connection.Response response = Jsoup
                .connect(String.format(CONTENT_URL, id))
                .method(Connection.Method.POST).header("Referer", baesURL)
                .ignoreContentType(true)
                .data(ImmutableMap.of("params", encText, "encSecKey", encSecKey))
                .timeout(8000)
                .execute();
        String responseReulst = response.body();
        if (StringUtils.isNotEmpty(responseReulst)) {
            JSONObject result = JSON.parseObject(responseReulst);
            JSONArray comments = result.getJSONArray("comments");
            if (comments != null && comments.size() > 0) {
                Iterator it = comments.iterator();
                while (it.hasNext()) {
                    JSONObject comment = (JSONObject) it.next();
                    String userid = comment.getJSONObject("user").get("userId").toString();
                    if (uid.equals(userid)) {
                        String commentstr = comment.get("content").toString();
                        System.err.println(pageIndex + "#####找到了一条留言信息,URL:" + baesURL);
                        System.err.println(commentstr);
                        System.err.println(pageIndex + "#####找到了一条留言信息,URL:" + baesURL);

                        if (userCommentList == null) userCommentList = new ArrayList<UserComment>();
                        userCommentList.add(new UserComment(pageIndex * 10, baesURL, commentstr, 0));
                    }
                    if (comment.get("beReplied") != null) {
                        JSONArray beReplieds = comment.getJSONArray("beReplied");
                        if (CollectionUtils.isNotEmpty(beReplieds)) {
                            Iterator itrep = beReplieds.iterator();
                            while (itrep.hasNext()) {
                                JSONObject becomment = (JSONObject) itrep.next();
                                String beuserid = becomment.getJSONObject("user").get("userId").toString();
                                if (uid.equals(beuserid)) {
                                    String commentstr = becomment.get("content").toString();
                                    System.err.println("#####找到了一条留言信息,URL:" + baesURL + ",pageIndex=" + pageIndex + "######");
                                    System.err.println(commentstr);
                                    if (userCommentList == null) userCommentList = new ArrayList<UserComment>();
                                    userCommentList.add(new UserComment(pageIndex * 10, baesURL, commentstr, 1));
                                }
                            }
                        }
                    }
                }
            } else {
                System.err.println(pageIndex + "....数据返回为空...");
            }
        }
    }

    /**
     * 获取用户的所有时间的100首歌曲
     *
     * @throws Exception
     */
    public void getUserRecardMusicList() throws Exception {
        String secKey = getSecKey();
        String encText = getAesEncrypt(secKey);
        String encSecKey = rsaEncrypt(secKey);
        Connection.Response response = Jsoup
                .connect(RECORD_URL)
                .method(Connection.Method.POST)
                .header("Referer", "http://music.163.com/user/home?id=260610597")
                .ignoreContentType(true)
                .data(ImmutableMap.of("params", encText, "encSecKey", encSecKey))
                .execute();
        System.err.println(response.body());

        String result = response.body();
        JSONObject jsonObjectResult = JSON.parseObject(result);
        Object allData = jsonObjectResult.get("allData");
        JSONArray alldataJsonArray = JSON.parseArray(allData.toString());
        Iterator<Object> it = alldataJsonArray.iterator();
        while (it.hasNext()) {
            JSONObject ob = (JSONObject) it.next();
            JSONObject songObj = JSON.parseObject(ob.get("song").toString());
            String songId = songObj.get("id").toString();
            songIdList.add(songId);
        }
    }

    /**
     * 根据用户Id获取用户名称
     *
     * @param userId
     * @return
     */
    private String getUserNameByUserId(int userId) {
        String result = "";
        String URL = "http://music.163.com/#/user/home?id=" + userId;
        Spider.create(new UserHomePageProcessor()).addUrl(URL).thread(5).run();
        return result;
    }


    private String getSecKey() {
        return new BigInteger(100, new SecureRandom()).toString(32).substring(0, 16);
    }

    private String getAesEncrypt(String secKey) throws Exception {

        return aesEncrypt(aesEncrypt(String.format(text, offset), "0CoJUm6Qyw8W8jud"), secKey);
    }

    private String getAesEncrypt2(String secKey) throws Exception {

        return aesEncrypt(aesEncrypt(String.format(replytext, offset), "0CoJUm6Qyw8W8jud"), secKey);
    }

    private String rsaEncrypt(String value) throws UnsupportedEncodingException {
        value = new StringBuilder(value).reverse().toString();
        BigInteger valueInt = hexToBigInteger(stringToHex(value));
        BigInteger pubkey = hexToBigInteger("010001");
        BigInteger modulus = hexToBigInteger("00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7");
        return valueInt.modPow(pubkey, modulus).toString(16);
    }

    private BigInteger hexToBigInteger(String hex) {
        return new BigInteger(hex, 16);
    }

    private String stringToHex(String text) throws UnsupportedEncodingException {
        return DatatypeConverter.printHexBinary(text.getBytes("UTF-8"));
    }


    public class UserComment {
        int offset;
        String songUrl;
        String comment;
        int isReply;

        public UserComment(int offset, String songUrl, String comment, int isReply) {
            this.offset = offset;
            this.songUrl = songUrl;
            this.comment = comment;
            this.isReply = isReply;
        }

        public int getOffset() {
            return offset;
        }

        public void setOffset(int offset) {
            this.offset = offset;
        }

        public String getSongUrl() {
            return songUrl;
        }

        public void setSongUrl(String songUrl) {
            this.songUrl = songUrl;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public int getIsReply() {
            return isReply;
        }

        public void setIsReply(int isReply) {
            this.isReply = isReply;
        }
    }


    public class UserHomePageProcessor implements PageProcessor {
        private Site site = Site.me().setRetryTimes(10).setSleepTime(1000);

        public void process(Page page) {

        }

        public Site getSite() {
            return this.site;
        }
    }
}
