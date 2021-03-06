/*
 * The MIT License (MIT)
 * 
 * Copyright � 2013 Clover Studio Ltd. All rights reserved.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cloverstudio.spika.couchdb;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.cloverstudio.spika.R;
import com.cloverstudio.spika.SpikaApp;
import com.cloverstudio.spika.WallActivity;
import com.cloverstudio.spika.couchdb.model.ActivitySummary;
import com.cloverstudio.spika.couchdb.model.Comment;
import com.cloverstudio.spika.couchdb.model.Emoticon;
import com.cloverstudio.spika.couchdb.model.Group;
import com.cloverstudio.spika.couchdb.model.GroupCategory;
import com.cloverstudio.spika.couchdb.model.GroupSearch;
import com.cloverstudio.spika.couchdb.model.Message;
import com.cloverstudio.spika.couchdb.model.User;
import com.cloverstudio.spika.couchdb.model.UserGroup;
import com.cloverstudio.spika.couchdb.model.UserSearch;
import com.cloverstudio.spika.couchdb.model.WatchingGroupLog;
import com.cloverstudio.spika.lazy.Emoticons;
import com.cloverstudio.spika.lazy.ImageLoader;
import com.cloverstudio.spika.management.FileManagement;
import com.cloverstudio.spika.management.SettingsManager;
import com.cloverstudio.spika.management.UsersManagement;
import com.cloverstudio.spika.utils.Const;
import com.cloverstudio.spika.utils.Logger;
import com.cloverstudio.spika.utils.Utils;
import com.google.gson.JsonObject;

/**
 * CouchDB
 * 
 * Creates and sends requests to CouchDB.
 */

public class CouchDB {

    private final static String groupCategoryCacheKey = "groupCategoryCacheKey";
    private static String TAG = "CouchDB: ";
    private static CouchDB sCouchDB;
    private static String sUrl;
    private static String sAuthUrl;
//    private static JSONParser sJsonParser = new JSONParser();
    private static HashMap<String,String> keyValueCache= new HashMap<String,String>();
    
    public CouchDB() {

        /* CouchDB credentials */

        sUrl = Const.API_URL;
        setAuthUrl(Const.AUTH_URL);
        sCouchDB = this;

        new ConnectionHandler();
    }

    public static void saveToMemCache(String key,String value){
        keyValueCache.put(key, value);
    }
    
    public static String getFromMemCache(String key){
        return keyValueCache.get(key);
    }
    
    public static CouchDB getCouchDB() {
        return sCouchDB;
    }

    public static String getUrl() {
        return sUrl;
    }
    
//***** UPLOAD FILE ****************************
    
    /**
     * Upload file
     * 
     * @param filePath
     * @return file ID
     * @throws IOException 
     * @throws ClientProtocolException 
     * @throws JSONException 
     * @throws UnsupportedOperationException 
     */
    
    public static String uploadFile(String filePath) throws SpikaException, ClientProtocolException, IOException, UnsupportedOperationException, JSONException  {

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        if (filePath != null && !filePath.equals("")) {
            params.add(new BasicNameValuePair(Const.FILE, filePath));
            String fileId = ConnectionHandler.getIdFromFileUploader(Const.FILE_UPLOADER_URL, params);
            return fileId;
        }
        return null;
    }

    public static void uploadFileAsync(String filePath, ResultListener<String> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, String>(new UploadFile(filePath), resultListener, context, showProgressBar).execute();
    }
    
    private static class UploadFile implements Command<String> 
    {
    	String filePath;
    	
    	public UploadFile (String filePath)
    	{
    		this.filePath = filePath;
    	}

		@Override
		public String execute() throws JSONException, IOException, IllegalStateException, SpikaException {
			return uploadFile(filePath);
		}
    }

//***** DOWNLOAD FILE ****************************
    
    /**
     * Download file
     * 
     * @param fileId
     * @param file
     * @return
     * @throws IOException 
     * @throws ClientProtocolException 
     * @throws JSONException 
     * @throws IllegalStateException 
     */
    public static File downloadFile(String fileId, File file) throws SpikaException, ClientProtocolException, IOException, IllegalStateException, JSONException {

        ConnectionHandler.getFile(Const.FILE_DOWNLOADER_URL + Const.FILE + "=" + fileId, file,
                UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());
        return file;
    }
    
    /**
     * @param fileId
     * @param file
     * @param resultListener
     * @param context
     * @param showProgressBar
     */
    public static void downloadFileAsync(String fileId, File file, ResultListener<File> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, File>(new DownloadFile(fileId, file), resultListener, context, showProgressBar).execute();
    }
    
    private static class DownloadFile implements Command<File>
    {
    	String fileId;
    	File file;
    	
    	public DownloadFile(String fileId, File file) {
			this.fileId = fileId;
			this.file = file;
		}

		@Override
		public File execute() throws JSONException, IOException, SpikaException {
			return downloadFile(fileId, file);
		}
    }
 
//***** UNREGISTER PUSH TOKEN ****************************
    
    /**
     * Unregister push token
     * 
     * @param userId
     * @return
     * @throws JSONException 
     * @throws SpikaException 
     * @throws IOException 
     * @throws IllegalStateException 
     * @throws ClientProtocolException 
     */
    public static String unregisterPushToken(String userId) throws ClientProtocolException, IllegalStateException, IOException, SpikaException, JSONException {
    	String result = ConnectionHandler.getString(Const.UNREGISTER_PUSH_URL + Const.USER_ID + "=" + userId,
                UsersManagement.getLoginUser().getId());
        return result;
    }
    
    public static void unregisterPushTokenAsync (String userId, ResultListener<String> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, String>(new UnregisterPushToken(userId), resultListener, context, showProgressBar).execute();
    }
    
    private static class UnregisterPushToken implements Command<String> {
    	
    	String userId;
    	
    	public UnregisterPushToken(String userId) {
			this.userId = userId;
		}

		@Override
		public String execute() throws JSONException, IOException, IllegalStateException, SpikaException {
			return unregisterPushToken(userId);
		}
    }
    
//***** AUTH ****************************  
    
    /**
     * @param email
     * @param password
     * @return
     * @throws IOException
     * @throws JSONException
     * @throws IllegalStateException
     * @throws SpikaException
     */
    public static String auth(String email, String password) throws IOException, JSONException, IllegalStateException, SpikaException {

        JSONObject jPost = new JSONObject();

        jPost.put("email", email);
        jPost.put("password", FileManagement.md5(password));

        JSONObject json = ConnectionHandler.postAuth(jPost);

        User user = null;

        user = CouchDBHelper.parseSingleUserObjectWithoutRowParam(json);
            
        if (user != null) {

        	SpikaApp.getPreferences().setUserToken(user.getToken());
        	SpikaApp.getPreferences().setUserEmail(user.getEmail());
        	SpikaApp.getPreferences().setUserId(user.getId());
        	SpikaApp.getPreferences().setUserPassword(password);

        	UsersManagement.setLoginUser(user);
        	UsersManagement.setToUser(user);
        	UsersManagement.setToGroup(null);

        	return Const.LOGIN_SUCCESS;
        } else {
        	return Const.LOGIN_ERROR;
        }
    }
    
    public static void authAsync(String email, String password, ResultListener<String> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, String>(new CouchDB.Auth(email, password), resultListener, context, showProgressBar).execute();
    }
    
    private static class Auth implements Command<String>
    {
    	String email;
    	String password;
    	
    	public Auth (String email, String password)
    	{
    		this.email = email;
    		this.password = password;
    	}

		@Override
		public String execute() throws JSONException, IOException, IllegalStateException, SpikaException {
			return auth(email, password);
		}
    }
    
//***** CREATE USER ****************************   
 
    /**
     * @param name
     * @param email
     * @param password
     * @return
     * @throws JSONException
     * @throws ClientProtocolException
     * @throws IOException
     * @throws IllegalStateException
     * @throws SpikaException
     */
    public static String createUser(String name, String email, String password) throws JSONException, ClientProtocolException, IOException, IllegalStateException, SpikaException {

    	JSONObject userJson = new JSONObject();

        userJson.put(Const.NAME, name);
        userJson.put(Const.PASSWORD, FileManagement.md5(password));
        userJson.put(Const.TYPE, Const.USER);
        userJson.put(Const.EMAIL, email);
        userJson.put(Const.LAST_LOGIN, Utils.getCurrentDateTime());
        userJson.put(Const.TOKEN_TIMESTAMP, Utils.getCurrentDateTime() / 1000);
        userJson.put(Const.TOKEN, Utils.generateToken());
        userJson.put(Const.MAX_CONTACT_COUNT, Const.MAX_CONTACTS);
        userJson.put(Const.MAX_FAVORITE_COUNT, Const.MAX_FAVORITES);
        userJson.put(Const.ONLINE_STATUS, Const.ONLINE);
        
        Log.e("Json", userJson.toString());
        
        return CouchDBHelper.createUser(ConnectionHandler.postJsonObject("createUser",userJson,
                Const.CREATE_USER, ""));
    }
    
    public static void createUserAsync(String name, String email, String password, ResultListener<String> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, String>(new CouchDB.CreateUser(name, email, password), resultListener, context, showProgressBar).execute();
    }
    
    private static class CreateUser implements Command<String>
    {
    	String name;
    	String email;
    	String password;
    	
    	public CreateUser(String name, String email, String password)
    	{
    		this.name = name;
    		this.email = email;
    		this.password = password;
    	}

		@Override
		public String execute() throws JSONException, IOException, IllegalStateException, SpikaException {
			return createUser(name, email, password);
		}
    }

//***** FIND USER BY NAME ****************************   
    
    /**
     * @param username
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     * @throws JSONException
     * @throws SpikaException
     */
    private static User findUserByName(String username) throws ClientProtocolException, IOException, JSONException, SpikaException {
    	
    	try {
    		username = URLEncoder.encode(username, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}

        final String url = Const.FIND_USER_BY_NAME + username;

        JSONObject jsonObject = ConnectionHandler.getJsonObject(url, null);
        
        User user = CouchDBHelper.parseSingleUserObjectWithoutRowParam(jsonObject);
        
        return user;
    }
    
    public static void findUserByNameAsync(String username, ResultListener<User> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, User>(new CouchDB.FindUserByName(username), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindUserByName implements Command<User>
    {
    	String username;
    	
    	public FindUserByName (String username)
    	{
    		this.username = username;
    	}
    	
		@Override
		public User execute() throws JSONException, IOException, SpikaException {
			return findUserByName(username);
		}
    }

//***** FIND USER BY MAIL ****************************   
    
    /**
     * Find user by email
     * 
     * @param email
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
	public static User findUserByEmail(String email) throws ClientProtocolException, IOException, JSONException, SpikaException {
        
        try {
            email = URLEncoder.encode(email, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

        JSONObject json = null;

        final String url = Const.FIND_USER_BY_EMAIL + email;
        User user = null;
        
        if (UsersManagement.getLoginUser() != null) {
        	json = ConnectionHandler.getJsonObject(url, UsersManagement.getLoginUser().getId());
        	user = CouchDBHelper.parseSingleUserObjectWithoutRowParam(json);
        } else { 
            json = ConnectionHandler.getJsonObject(url, "");
            user = CouchDBHelper.parseSingleUserObjectWithoutRowParam(json);
        }
        
        return user;
    }
    
    public static void findUserByEmailAsync(String email, ResultListener<User> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, User>(new CouchDB.FindUserByEmail(email), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindUserByEmail implements Command<User>
    {
    	String email;
    	
    	public FindUserByEmail(String email)
    	{
    		this.email = email;
    	}

		@Override
		public User execute() throws JSONException, IOException, SpikaException {
			return findUserByEmail(email);
		}
    }
    
//***** FIND USER BY ID ***********************************
    
    /**
     * Find user by id
     * 
     * @param id
     * @return
     * @throws JSONException 
     * @throws SpikaException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static User findUserById(String id) throws JSONException, ClientProtocolException, IOException, SpikaException {
    	
        try {
            id = URLEncoder.encode(id, "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
            return null;
        }

        String url = Const.FIND_USER_BY_ID + id;
        JSONObject json = ConnectionHandler.getJsonObject(url, UsersManagement.getLoginUser().getId());
        User user = CouchDBHelper.parseSingleUserObjectWithoutRowParam(json);
        
        return user;
    }
    
    public static void findUserByIdAsync(String id, ResultListener<User> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, User>(new CouchDB.FindUserById(id), resultListener, context, showProgressBar).execute();
    }

    public static class FindUserById implements Command<User>
    {
    	String id;
    	
    	public FindUserById (String id)
    	{
    		this.id = id;
    	}
    	
		@Override
		public User execute() throws JSONException, IOException, SpikaException {
			return findUserById(id);
		}
    }
    
//******* UPDATE USER ***************************
    
    /**
     * Method used for updating user attributes
     * 
     * If you add some new attributes to user object you must also add code to
     * add that data to userJson
     * 
     * @param user
     * @return user object
     * @throws SpikaException 
     * @throws JSONException 
     * @throws ClientProtocolException 
     * @throws IOException 
     * @throws IllegalStateException 
     */
    public static boolean updateUser(User user) throws JSONException, ClientProtocolException, IllegalStateException, IOException, SpikaException {

        JSONObject userJson = new JSONObject();
        List<String> contactIds = new ArrayList<String>();
        List<String> groupIds = new ArrayList<String>();

        JSONObject json = null;
        
        /* General user info */
        userJson.put(Const._ID, user.getId());
        userJson.put(Const._REV, user.getRev());
        userJson.put(Const.EMAIL, user.getEmail());
        userJson.put(Const.NAME, user.getName());
        userJson.put(Const.TYPE, Const.USER);
        userJson.put(Const.PASSWORD, FileManagement.md5(SpikaApp.getPreferences().getUserPassword()));
        userJson.put(Const.LAST_LOGIN, user.getLastLogin());
        userJson.put(Const.ABOUT, user.getAbout());
        userJson.put(Const.BIRTHDAY, user.getBirthday());
        userJson.put(Const.GENDER, user.getGender());
        userJson.put(Const.TOKEN, SpikaApp.getPreferences().getUserToken());
        userJson.put(Const.TOKEN_TIMESTAMP, user.getTokenTimestamp());
        userJson.put(Const.ANDROID_PUSH_TOKEN, SpikaApp.getPreferences().getUserPushToken());
        userJson.put(Const.ONLINE_STATUS, user.getOnlineStatus());
        userJson.put(Const.AVATAR_FILE_ID, user.getAvatarFileId());
        userJson.put(Const.MAX_CONTACT_COUNT, user.getMaxContactCount());
        userJson.put(Const.AVATAR_THUMB_FILE_ID, user.getAvatarThumbFileId());

        /* Set users favorite contacts */
        JSONArray contactsArray = new JSONArray();
        contactIds = user.getContactIds();
        if (!contactIds.isEmpty()) {
            for (String id : contactIds) {
                contactsArray.put(id);
            }
        }
        if (contactsArray.length() > 0) {
            userJson.put(Const.CONTACTS, contactsArray);
        }

        /* Set users favorite groups */
        JSONArray groupsArray = new JSONArray();
        groupIds = user.getGroupIds();

        if (!groupIds.isEmpty()) {
            for (String id : groupIds) {
                groupsArray.put(id);
            }
        }

        if (groupsArray.length() > 0) {
            userJson.put(Const.FAVORITE_GROUPS, groupsArray);
        }
        
        json = ConnectionHandler.postJsonObject(Const.UPDATE_USER, userJson, user.getId(), user.getToken());
        
        return CouchDBHelper.updateUser(json, contactIds, groupIds);
    }
    
    public static void updateUserAsync (User user, ResultListener<Boolean> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Boolean>(new UpdateUser(user), resultListener, context, showProgressBar).execute();;
    }
    
    private static class UpdateUser implements Command<Boolean>
    {
    	User user;
    	
    	public UpdateUser (User user)
    	{
    		this.user = user;
    	}

		@Override
		public Boolean execute() throws JSONException, IOException, IllegalStateException, SpikaException {
			return updateUser(user);
		}
    }

//************ SEARCH USERS ***************  
    
    /**
     * Finds users given the search criteria in userSearch
     * 
     * @param userSearch
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static List<User> searchUsers(UserSearch userSearch) throws ClientProtocolException, IOException, JSONException, SpikaException {

        String searchParams = "";

        if (userSearch.getName() != null) {
            try {
                userSearch.setName(URLEncoder.encode(userSearch.getName(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return null;
            }
            searchParams = "n=" + userSearch.getName();
        }

        if (userSearch.getFromAge() != null && !"".equals(userSearch.getFromAge())) {
            searchParams += "&af=" + userSearch.getFromAge();
        }
        if (userSearch.getToAge() != null && !"".equals(userSearch.getToAge())) {
            searchParams += "&at=" + userSearch.getToAge();
        }
        if (userSearch.getGender() != null
                && (userSearch.getGender().equals(Const.FEMALE) || userSearch.getGender().equals(
                        Const.MALE))) {
            searchParams += "&g=" + userSearch.getGender();
        }
        if (userSearch.getOnlineStatus() != null && !userSearch.getOnlineStatus().equals("")) {
        	searchParams += "&status=" + userSearch.getOnlineStatus();
        }
        
        Logger.error("Search", Const.SEARCH_USERS_URL + searchParams);

        JSONArray json = ConnectionHandler.getJsonArray(Const.SEARCH_USERS_URL + searchParams,
                UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());

        return CouchDBHelper.parseSearchUsersResult(json);
    }
    
    public static void searchUsersAsync(UserSearch userSearch, ResultListener<List<User>> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, List<User>>(new SearchUsers(userSearch), resultListener, context, showProgressBar).execute();
    }
    
    private static class SearchUsers implements Command<List<User>>
    {
    	UserSearch userSearch;
    	
    	public SearchUsers (UserSearch userSearch)
    	{
    		this.userSearch = userSearch;
    	}

		@Override
		public List<User> execute() throws JSONException, IOException, SpikaException {
			return searchUsers(userSearch);
		}
    }

//************ GET GROUP BY NAME **********************
    
    /**
     * Returns group by group name
     * 
     * @param groupname
     *            String value that will be used for search
     * @return true if the provided string is already taken email, otherwise
     *         false
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static Group getGroupByName(String groupname) throws ClientProtocolException, IOException, JSONException, SpikaException {
    	
    	try {
    		groupname = URLEncoder.encode(groupname, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}

        final String URL = Const.FIND_GROUP_BY_NAME + groupname;

        JSONObject jsonObject = ConnectionHandler.getJsonObject(URL, UsersManagement.getLoginUser().getId());

        Group group = null;

        group = CouchDBHelper.parseSingleGroupObjectWithoutRowParam(jsonObject);

        return group;
    }
    
    public static void getGroupByNameAsync(String groupname, ResultListener<Group> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Group>(new CouchDB.GetGroupByName(groupname), resultListener, context, showProgressBar).execute();
    }
    
    private static class GetGroupByName implements Command<Group>
    {
    	String groupname;
    	
    	public GetGroupByName (String groupname)
    	{
    		this.groupname = groupname;
    	}
    	
		@Override
		public Group execute() throws JSONException, IOException, SpikaException {
			return getGroupByName(groupname);
		}
    }

//**************** SEARCH GROUPS BY NAME **************************
    
    /**
     * Finds groups given the search criteria in groupSearch
     * 
     * @param groupSearch
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static List<Group> searchGroups(GroupSearch groupSearch) throws ClientProtocolException, IOException, JSONException, SpikaException {

        String searchParams = "";

        if (groupSearch.getName() != null) {
            try {
                groupSearch.setName(URLEncoder.encode(groupSearch.getName(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return null;
            }
            searchParams = groupSearch.getName();
        }

        JSONArray json = ConnectionHandler.getJsonArray(Const.SEARCH_GROUPS_URL + searchParams,
                UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());

        return CouchDBHelper.parseSearchGroupsResult(json);
    }
    
    public static void searchGroupsAsync (GroupSearch groupSearch, ResultListener<List<Group>> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, List<Group>>(new SearchGroups(groupSearch), resultListener, context, showProgressBar).execute();
    }
    
    private static class SearchGroups implements Command<List<Group>>
    {
    	GroupSearch groupSearch;
    	
    	public SearchGroups (GroupSearch groupSearch)
    	{
    		this.groupSearch = groupSearch;
    	}

		@Override
		public List<Group> execute() throws JSONException, IOException, SpikaException {
			return searchGroups(groupSearch);
		}
    }

//**************** FIND AVATAR FILE ID **************************

    public static String findAvatarFileId(String userId) throws ClientProtocolException, IOException, JSONException, SpikaException {

        try {
            userId = URLEncoder.encode(userId, "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
            return null;
        }

        JSONObject json = ConnectionHandler.getJsonObject(Const.GET_AVATAR_FILE_ID + userId, UsersManagement.getLoginUser().getId());
		return CouchDBHelper.findAvatarFileId(json);
		
    }
    
    public static void findAvatarByIdAsync (String userId, ResultListener<String> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, String>(new FindAvatarFileId(userId), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindAvatarFileId implements Command<String>
    {
    	String userId;
    	
    	public FindAvatarFileId (String userId)
    	{
    		this.userId = userId; 
    	}

		@Override
		public String execute() throws JSONException, IOException, SpikaException {
			return findAvatarFileId(userId);
		}
    }
    
    public static void findAvatarAndDisplay (String userId, ImageView view, Context context) {
    	findAvatarByIdAsync(userId, new FindAvatarResultListener(view), context, false);
    }
    
    private static class FindAvatarResultListener implements ResultListener<String> {
    	
    	ImageView view;
    	
    	public FindAvatarResultListener(ImageView view) {
			this.view = view;
		}
    	
		@Override
		public void onResultsSucceded(String result) {
			Utils.displayImage(result, view, ImageLoader.SMALL, R.drawable.user_stub, false);
		}
		@Override
		public void onResultsFail() {			
		}
    }
 
//************ FIND USER CONTACTS ***********************    
    

    /**
     * @param id
     * @return
     * @throws JSONException
     * @throws IOException
     * @throws SpikaException
     */
    public static List<User> findUserContacts(String id) throws JSONException, IOException, SpikaException {
    	
    	List<User> contacts = new ArrayList<User>();
    	
    	User user = findUserById(id);
        List<String> contactIds = user.getContactIds();
        for (String contactId : contactIds) {
			contacts.add(findUserById(contactId));
		}
        
        return contacts;
    }

    public static void findUserContactsAsync (String id, ResultListener<List<User>> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, List<User>>(new FindUserContacts(id), resultListener, context, showProgressBar).execute();
    }
    
    public static class FindUserContacts implements Command<List<User>>
    {
    	String id;
    	
    	public FindUserContacts (String id)
    	{
    		this.id = id;
    	}

		@Override
		public List<User> execute() throws JSONException, IOException, SpikaException {
	        return findUserContacts(id);
		}
    }

  //************ ADD USER CONTACT ***********************
    
    public static void addUserContactAsync (final String userId, final ResultListener<Boolean> resultListener, final Context context, final boolean showProgressBar)
    {
    	 new SpikaAsyncTask<Void, Void, Boolean>(new AddUserContact(userId), resultListener, context, showProgressBar).execute();
    }
    
    private static class AddUserContact implements Command<Boolean> 
    {
    	String userId;
    	
    	public AddUserContact(String userId) {
			this.userId = userId;
		}

		@Override
		public Boolean execute() throws JSONException, IOException, SpikaException {
			addUser(userId);
			return true;
		}
    }
    
    private static void addUser (String contactId) throws JSONException, ClientProtocolException, IllegalStateException, IOException, SpikaException {
    	User user = UsersManagement.getLoginUser();
    	
    	JSONObject jsonRequest = new JSONObject();
    	jsonRequest.put(Const._ID, user.getId());
    	jsonRequest.put(Const.USER_ID, contactId);
        
        JSONObject jsonResponse = ConnectionHandler.postJsonObject(Const.ADD_CONTACT, jsonRequest, user.getId(), user.getToken());
        UsersManagement.setLoginUser(CouchDBHelper.parseSingleUserObjectWithoutRowParam(jsonResponse));        
    }

  //************ REMOVE USER CONTACT ***********************
    
    public static void removeUserContactAsync (String userId, ResultListener<Boolean> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Boolean>(new RemoveUserContact(userId), resultListener, context, showProgressBar).execute();
    }
    
    private static class RemoveUserContact implements Command<Boolean>
    {
    	String userId;
    	
    	public RemoveUserContact(String userId) {
			this.userId = userId;
		}

		@Override
		public Boolean execute() throws JSONException, IOException, SpikaException {
			removeContact(userId);
			return true;
		}
    }
    
    private static void removeContact (String contactId) throws JSONException, ClientProtocolException, IllegalStateException, IOException, SpikaException {
    	User user = UsersManagement.getLoginUser();
    	
    	JSONObject jsonRequest = new JSONObject();
    	jsonRequest.put(Const._ID, user.getId());
    	jsonRequest.put(Const.USER_ID, contactId);
        
        JSONObject jsonResponse = ConnectionHandler.postJsonObject(Const.REMOVE_CONTACT, jsonRequest, user.getId(), user.getToken());
        UsersManagement.setLoginUser(CouchDBHelper.parseSingleUserObjectWithoutRowParam(jsonResponse));
    }
    
    /**
     * Find all groups
     * 
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static List<Group> findAllGroups() throws ClientProtocolException, IOException, JSONException, SpikaException {

        JSONObject json= ConnectionHandler.getJsonObject(Const.FIND_GROUP_BY_NAME, UsersManagement.getLoginUser().getId());
        return CouchDBHelper.parseMultiGroupObjects(json);
    }
    
    public static void findAllGroupsAsync (ResultListener<List<Group>> resultListener, Context context, boolean showProgressBar)
    {
    	new SpikaAsyncTask<Void, Void, List<Group>>(new FindAllGroups(), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindAllGroups implements Command<List<Group>>
    {
		@Override
		public List<Group> execute() throws JSONException, IOException, SpikaException {
			return findAllGroups();
		}	
    }

//************************* FIND GROUP BY ID ****************************
    /**
     * Find group by id
     * 
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */

    public static Group findGroupById(String id) throws ClientProtocolException, IOException, JSONException, SpikaException {

        try {
            id = URLEncoder.encode(id, "UTF-8");
        } catch (UnsupportedEncodingException e) {

            e.printStackTrace();
            return null;
        }

        JSONObject json = ConnectionHandler.getJsonObject(Const.FIND_GROUP_BY_ID + id, UsersManagement.getLoginUser().getId());

        return CouchDBHelper.parseSingleGroupObjectWithoutRowParam(json);
    }
    
    public static void findGroupByIdAsync(String id, ResultListener<Group> resultListener, Context context, boolean showProgressBar)
    {
    	new SpikaAsyncTask<Void, Void, Group>(new FindGroupById(id), resultListener, context, showProgressBar).execute();
    }
    
    public static class FindGroupById implements Command<Group>
    {
    	String id;
    	
    	public FindGroupById (String id)
    	{
    		this.id = id;
    	}

		@Override
		public Group execute() throws JSONException, IOException, SpikaException {
			return findGroupById(id);
		}
    }

//********************** FIND GROUP BY NAME ************************
    
    /**
     * Find group/groups by name
     * 
     * @param name
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    //TODO: it should be only one group.... or search by name??? 
    public static List<Group> findGroupsByName(String name) throws ClientProtocolException, IOException, JSONException, SpikaException {

        try {
            name = URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();

            return null;
        }

        String url = Const.FIND_GROUP_BY_NAME + name;

        JSONObject json = ConnectionHandler.getJsonObject(url, UsersManagement.getLoginUser().getId());

        return CouchDBHelper.parseMultiGroupObjects(json);
    }
    
    public static void findGroupsByNameAsync (String name, ResultListener<List<Group>> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, List<Group>>(new FindGroupsByName(name), resultListener, context, showProgressBar).execute();
    }
    
    public static class FindGroupsByName implements Command<List<Group>>
    {
    	String name;

		public FindGroupsByName(String name)
    	{
    		this.name = name;
    	}

		@Override
		public List<Group> execute() throws JSONException, IOException, SpikaException {
			return findGroupsByName(name);
		}
    }

//**************** FIND USER FAVORITE GROUPS **********************
    
    /**
     * Find users favorite groups
     * 
     * @param id
     * @return
     * @throws SpikaException 
     * @throws IOException 
     * @throws JSONException 
     * @throws ClientProtocolException 
     */
    public static List<Group> findUserFavoriteGroups(String id) throws ClientProtocolException, JSONException, IOException, SpikaException {

    	ArrayList<Group> groups = new ArrayList<Group>();
    	
    	User user = findUserById(id);
    	
    	for (String groupId : user.getGroupIds()) {
			groups.add(findGroupById(groupId));
		}
    	
    	return groups;
    }
    
    public static void findUserFavoriteGroupsAsync (String id, ResultListener<List<Group>> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, List<Group>>(new FindUserFavoriteGroups(id), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindUserFavoriteGroups implements Command<List<Group>>
    {
    	String id;
    	
    	public FindUserFavoriteGroups (String id)
    	{
    		this.id = id;
    	}

		@Override
		public List<Group> execute() throws JSONException, IOException, SpikaException {
			return findUserFavoriteGroups(id);
		}
    }

//***** FIND USER ACTIVITY SUMMARY ***************************
    
    /**
     * Find activity summary
     * 
     * @param id
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    private static ActivitySummary findUserActivitySummary(String id) throws ClientProtocolException, IOException, JSONException, SpikaException {
        String url = Const.FIND_USERACTIVITY_SUMMARY;
        JSONObject json = ConnectionHandler.getJsonObject(url, id);
        return CouchDBHelper.parseSingleActivitySummaryObject(json);
    }
    
    public static void findUserActivitySummary(String id, ResultListener<ActivitySummary> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, ActivitySummary>(new CouchDB.FindUserActivitySummary(id), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindUserActivitySummary implements Command<ActivitySummary>
    {
    	String id;
    	
    	public FindUserActivitySummary (String id)
    	{
    		this.id = id;
    	}

		@Override
		public ActivitySummary execute() throws JSONException, IOException, SpikaException {
			return findUserActivitySummary(id);
		}
    }

// ************** CREATE GROUP ***********************   
    
    public static String createGroup(Group group) throws JSONException, IllegalStateException, IOException, SpikaException {

        JSONObject groupJson = new JSONObject();

        groupJson.put(Const.NAME, group.getName());
        groupJson.put(Const.GROUP_PASSWORD, FileManagement.md5(group.getPassword()));
        groupJson.put(Const.TYPE, Const.GROUP);
        groupJson.put(Const.USER_ID, UsersManagement.getLoginUser().getId());
        groupJson.put(Const.DESCRIPTION, group.getDescription());
        groupJson.put(Const.AVATAR_FILE_ID, group.getAvatarFileId());
        groupJson.put(Const.AVATAR_THUMB_FILE_ID, group.getAvatarThumbFileId());
        groupJson.put(Const.CATEGORY_ID, group.getCategoryId());
        groupJson.put(Const.CATEGORY_NAME, group.getCategoryName());
        groupJson.put(Const.DELETED, false);

        return CouchDBHelper.createGroup(ConnectionHandler.postJsonObject(Const.CREATE_GROUP, groupJson,
                UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken()));
    }
    
    public static void createGroupAsync(Group group, ResultListener<String> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, String>(new CreateGroup(group), resultListener, context, showProgressBar).execute();
    }
    
    private static class CreateGroup implements Command<String>
    {
    	Group group;
    	
    	public CreateGroup(Group group)
    	{
    		this.group = group;
    	}

		@Override
		public String execute() throws JSONException, IOException, IllegalStateException, SpikaException {
			return createGroup(group);
		}
    }

//*************** UPDATE GROUP ************************
    
    /**
     * Update a group you own
     * 
     * @param group
     * @return
     * @throws SpikaException 
     * @throws IOException 
     * @throws JSONException 
     * @throws IllegalStateException 
     * @throws ClientProtocolException 
     */
    public static boolean updateGroup(Group group) throws ClientProtocolException, IllegalStateException, JSONException, IOException, SpikaException {

        JSONObject groupJson = new JSONObject();

        groupJson.put(Const.NAME, group.getName());
        groupJson.put(Const.GROUP_PASSWORD, FileManagement.md5(group.getPassword()));
        groupJson.put(Const.TYPE, Const.GROUP);
        groupJson.put(Const.USER_ID, UsersManagement.getLoginUser().getId());
        groupJson.put(Const.DESCRIPTION, group.getDescription());
        groupJson.put(Const.AVATAR_FILE_ID, group.getAvatarFileId());
        groupJson.put(Const.AVATAR_THUMB_FILE_ID, group.getAvatarThumbFileId());
        groupJson.put(Const._REV, group.getRev());
        groupJson.put(Const._ID, group.getId());
        groupJson.put(Const.CATEGORY_ID, group.getCategoryId());
        groupJson.put(Const.CATEGORY_NAME, group.getCategoryName());
        groupJson.put(Const.DELETED, group.isDeleted());
        
        //TODO: Check if this works automagiclly
//        if (group.isDeleted()) {
//        	List<UserGroup> usersGroup = new ArrayList<UserGroup>(findUserGroupByIds(group.getId(),
//                    UsersManagement.getLoginUser().getId()));
//            if (usersGroup != null) {
//                for (UserGroup userGroup : usersGroup) {
//                    CouchDB.deleteGroup(userGroup.getId(), userGroup.getRev());
//                }
//            }
//        }
        
        JSONObject newGroupJson = ConnectionHandler.postJsonObject(Const.UPDATE_GROUP, groupJson, UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());
        return CouchDBHelper.updateGroup(newGroupJson);
    }

    public static void updateGroupAsync (Group group, ResultListener<Boolean> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Boolean>(new UpdateGroup(group), resultListener, context, showProgressBar).execute();
    }
    
    private static class UpdateGroup implements Command<Boolean> {
    	
    	Group group;
    	
    	public UpdateGroup(Group group) {
			this.group = group;
		}

		@Override
		public Boolean execute() throws JSONException, IOException, IllegalStateException, SpikaException {
			return updateGroup(group);
		}
    }
    
 // ***************** ADD FAVORITE GROUP ***************************   
    
    /**
     * Add favorite user groups to current logged in user
     * 
     * @param groupId
     * @return
     * @throws SpikaException 
     * @throws IOException 
     * @throws JSONException 
     * @throws ClientProtocolException 
     */
    public static void addFavoriteGroup(String groupId) throws ClientProtocolException, JSONException, IOException, SpikaException {
    	
    	JSONObject create = new JSONObject();
    	create.put(Const.GROUP_ID, groupId);
    	
    	JSONObject userJson = ConnectionHandler.postJsonObject(Const.SUBSCRIBE_GROUP, create, UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());
    	UsersManagement.setLoginUser(CouchDBHelper.parseSingleUserObjectWithoutRowParam(userJson));
    }
    
    public static void addFavoriteGroupAsync (final String groupId, final ResultListener<Boolean> resultListener, final Context context, final boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Boolean>(new AddFavoriteGroup(groupId), resultListener, context, showProgressBar).execute();
    }
    
    private static class AddFavoriteGroup implements Command<Boolean> {
    	
    	String groupId;
    	
    	public AddFavoriteGroup(String groupId) {
			this.groupId = groupId;
		}

		@Override
		public Boolean execute() throws JSONException, IOException, SpikaException {
			addFavoriteGroup(groupId);
			return true;
		}
    }

// ******************** REMOVE FAVORITE GROUP *************************
    
    /**
     * Remove a group from favorite user groups of current logged in user
     * 
     * @param groupId
     * @return
     * @throws SpikaException 
     * @throws IOException 
     * @throws JSONException 
     * @throws ClientProtocolException 
     */
    public static void removeFavoriteGroup(String groupId) throws ClientProtocolException, JSONException, IOException, SpikaException {

    	JSONObject create = new JSONObject();
    	create.put(Const.GROUP_ID, groupId);
    	
    	JSONObject userJson = ConnectionHandler.postJsonObject(Const.UNSUBSCRIBE_GROUP, create, UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());
    	UsersManagement.setLoginUser(CouchDBHelper.parseSingleUserObjectWithoutRowParam(userJson));
    }
    
    public static void removeFavoriteGroupAsync (String groupId, ResultListener<Boolean> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Boolean>(new RemoveFavoriteGroup(groupId), resultListener, context, showProgressBar).execute();
    }
    
    private static class RemoveFavoriteGroup implements Command<Boolean> {
    	
    	String groupId;
    	
    	public RemoveFavoriteGroup(String groupId) {
			this.groupId = groupId;
		}

		@Override
		public Boolean execute() throws JSONException, IOException, SpikaException {
			removeFavoriteGroup(groupId);
			return true;
		}
    }

// **************** DELETE GROUP *********************************    
    
    private static boolean deleteGroup(String id, String rev) throws JSONException, ClientProtocolException, IllegalStateException, IOException, SpikaException {

    	JSONObject create = new JSONObject();
    	create.put(Const._ID, id);
    	
    	JSONObject delete = ConnectionHandler.postJsonObject(Const.DELETE_GROUP, create, UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());
    	    	
    	return CouchDBHelper.deleteUserGroup(delete);	
    }
    
    public static void deleteGroupAsync (String id, ResultListener<Boolean> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Boolean>(new DeleteGroup(id), resultListener, context, showProgressBar).execute();
    }
    
    private static class DeleteGroup implements Command<Boolean>{

    	String id;
    	
    	public DeleteGroup (String id) {
    		this.id = id;
    	}
    	
		@Override
		public Boolean execute() throws JSONException, IOException,
				SpikaException {
			return deleteGroup(id, "");
		}
    	
    }

    /**
     * Find user group by group id and user id
     * 
     * @param groupId
     * @param userId
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    
    //TODO: is this needed???
    private static List<UserGroup> findUserGroupByIds(String groupId, String userId) throws ClientProtocolException, IOException, JSONException, SpikaException {

        String key = "[\"" + groupId + "\",\"" + userId + "\"]";

        try {
            key = URLEncoder.encode(key, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();

            return null;
        }

        JSONObject json = ConnectionHandler.getJsonObject(sUrl
                + "_design/app/_view/find_users_group?key=" + key, UsersManagement.getLoginUser()
                .getId());

        return CouchDBHelper.parseMultiUserGroupObjects(json);
    }

    
    /**
     * Find users group by group id
     * 
     * @param groupId
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    //TODO: is this needed????
    public static List<UserGroup> findUserGroupsByGroupId(String groupId) throws ClientProtocolException, IOException, JSONException, SpikaException {

        String key = "\"" + groupId + "\"";

        try {
            key = URLEncoder.encode(key, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();

            return null;
        }

        JSONObject json = ConnectionHandler.getJsonObject(sUrl
                + "_design/app/_view/find_users_by_groupid?key=" + key, UsersManagement
                .getLoginUser().getId());

        return CouchDBHelper.parseMultiUserGroupObjects(json);
    }
    
    public static void findUserGroupsByGroupId(String groupId, ResultListener<List<UserGroup>> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, List<UserGroup>>(new FindUserGroupsByGroupId(groupId), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindUserGroupsByGroupId implements Command<List<UserGroup>> {
    	
    	String groupId;
    	
    	public FindUserGroupsByGroupId(String groupId) {
			this.groupId = groupId;
		}

		@Override
		public List<UserGroup> execute() throws JSONException, IOException, SpikaException {
			return findUserGroupsByGroupId(groupId);
		}	
    }

//************** FING GROUP CATEGORIES *********************
    
    /**
     * Get a list of group categories from database
     * 
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static List<GroupCategory> findGroupCategories() throws ClientProtocolException, IOException, JSONException, SpikaException {

        String cachedJSON = CouchDB.getFromMemCache(groupCategoryCacheKey);
        
        if(cachedJSON == null){

        	JSONObject json = ConnectionHandler.getJsonObject(Const.FIND_GROUP_CATEGORIES, UsersManagement.getLoginUser().getId());
        	
            CouchDB.saveToMemCache(groupCategoryCacheKey, json.toString());
            
            return CouchDBHelper.parseMultiGroupCategoryObjects(json);
            
        }else{
            
            try {
                
                JSONObject json =  new JSONObject(cachedJSON);
                
                return CouchDBHelper.parseMultiGroupCategoryObjects(json);
                
            } catch (JSONException e) {

                CouchDB.saveToMemCache(groupCategoryCacheKey, null);
                
                return findGroupCategories();
            }
            
        }        
    }
    
    public static void findGroupCategoriesAsync(ResultListener<List<GroupCategory>> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, List<GroupCategory>>(new FindGroupCategories(), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindGroupCategories implements Command<List<GroupCategory>>
    {
		@Override
		public List<GroupCategory> execute() throws JSONException, IOException, SpikaException {
			return findGroupCategories();
		}
    }

//************* FIND GROUP BY CATEGORY ID ********************   
    
    /**
     * Find group by category id
     * 
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static List<Group> findGroupByCategoryId(String id) throws ClientProtocolException, IOException, JSONException, SpikaException {

        try {
            id = URLEncoder.encode(id, "UTF-8");
        } catch (UnsupportedEncodingException e) {

            e.printStackTrace();
            return null;
        }

        JSONObject json = ConnectionHandler.getJsonObject(Const.FIND_GROUP_BY_CATEGORY_ID + id, UsersManagement.getLoginUser().getId());
        
        return CouchDBHelper.parseMultiGroupObjects(json);
    }
    
    public static void findGroupByCategoryIdAsync(String id, ResultListener<List<Group>> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, List<Group>>(new FindGroupByCategoryId(id), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindGroupByCategoryId implements Command<List<Group>> {
    	
    	String id;
    	
    	public FindGroupByCategoryId(String id) {
			this.id = id;
		}

		@Override
		public List<Group> execute() throws JSONException, IOException, SpikaException {
			return findGroupByCategoryId(id);
		}
    }



  //************** FIND MESSAGE BY ID **************************
    
    /**
     * Find a single message by ID
     * 
     * @param id
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static Message findMessageById(String id) throws ClientProtocolException, IOException, JSONException, SpikaException {

        JSONObject json = ConnectionHandler.getJsonObject(Const.FIND_MESSAGE_BY_ID + id, UsersManagement.getLoginUser().getId());

        return CouchDBHelper.findMessage(json);
    }

    public static void findMessageByIdAsync(String id, ResultListener<Message> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Message>(new FindMessageById(id), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindMessageById implements Command<Message> {
    	
    	String id;
    	
    	public FindMessageById (String id) {
    		this.id = id;
    	}

		@Override
		public Message execute() throws JSONException, IOException, SpikaException {
			return findMessageById(id);
		}
    }

//************** FIND MESSAGES FOR USER **************************
    
    /**
     * Find messages sent to user
     * 
     * @param from
     * @param page
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static ArrayList<Message> findMessagesForUser(User from, int page) throws ClientProtocolException, IOException, JSONException, SpikaException {

        int skip = page * SettingsManager.sMessageCount;
        int count = 20;

        if (UsersManagement.isTheSameUser()) {
            count = SettingsManager.sMessageCount * 2;
            skip *= 2;
        } else {
            count = SettingsManager.sMessageCount;
        }

        User to_user = UsersManagement.getToUser();
        Group to_group = UsersManagement.getToGroup();

        String url = "";
        String _from = from.getId();

        try {
            _from = URLEncoder.encode(_from, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        String _to = "";

        if (to_user != null) {
            _to = to_user.getId();
            url = Const.FIND_USER_MESSAGES + _to + "/" + count + "/" + skip;
        } else if (to_group != null) {
        	_to = to_group.getId();
            url = Const.FIND_GROUP_MESSAGES + _to + "/" + count + "/" + skip;
        }

        try {
            _to = URLEncoder.encode(_to, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        
        JSONObject json = ConnectionHandler.getJsonObject(url, UsersManagement.getLoginUser().getId());

        return CouchDBHelper.findMessagesForUser(json);
    }
    
    public static void findMessagesForUserAsync(User from, int page, ResultListener<ArrayList<Message>> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, ArrayList<Message>>(new FindMessagesForUser(from, page), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindMessagesForUser implements Command<ArrayList<Message>> {
    	
    	User from;
    	int page; 
    	
    	public FindMessagesForUser(User from, int page) {
    		this.from = from;
    		this.page = page;
    	}

		@Override
		public ArrayList<Message> execute() throws JSONException, IOException,
				SpikaException {
			return findMessagesForUser(from, page);
		}
    } 

//******* SEND MESSAGE TO USER *************
    
    /**
     * Send message to user
     * 
     * @param m
     * @param isPut
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws IllegalStateException 
     * @throws ClientProtocolException 
     */
    public static boolean sendMessageToUser(Message m) throws ClientProtocolException, IllegalStateException, IOException, JSONException, SpikaException {

        boolean isSuccess = true;
        JSONObject resultOfCouchDB = null;
        JSONObject jsonObj = new JSONObject();

        jsonObj.put(Const.MESSAGE_TYPE, m.getMessageType());
        jsonObj.put(Const.MODIFIED, m.getModified());
        jsonObj.put(Const.TYPE, m.getType());
        jsonObj.put(Const.FROM_USER_NAME, m.getFromUserName());
        jsonObj.put(Const.FROM_USER_ID, m.getFromUserId());
        jsonObj.put(Const.VALID, m.isValid());
        jsonObj.put(Const.MESSAGE_TARGET_TYPE, m.getMessageTargetType());
        jsonObj.put(Const.CREATED, m.getCreated());
        jsonObj.put(Const.TO_USER_NAME, m.getToUserName());
        jsonObj.put(Const.TO_USER_ID, m.getToUserId());
        jsonObj.put(Const.BODY, m.getBody());
        if (!m.getLatitude().equals("")) {
            jsonObj.put(Const.LATITUDE, m.getLatitude());
        }
        if (!m.getLongitude().equals("")) {
            jsonObj.put(Const.LONGITUDE, m.getLongitude());
        }
        if (!m.getAttachments().equals("")) {

            jsonObj.put(Const.ATTACHMENTS, new JSONObject(m.getAttachments()));
        }
        if (!m.getVideoFileId().equals("")) {
            jsonObj.put(Const.VIDEO_FILE_ID, m.getVideoFileId());
        }
        if (!m.getVoiceFileId().equals("")) {
            jsonObj.put(Const.VOICE_FILE_ID, m.getVoiceFileId());
        }
        if (!m.getImageFileId().equals("")) {
            jsonObj.put(Const.PICTURE_FILE_ID, m.getImageFileId());
        }
        if (!m.getImageThumbFileId().equals("")) {
            jsonObj.put(Const.PICTURE_THUMB_FILE_ID, m.getImageFileId());
        }
        if (!m.getEmoticonImageUrl().equals("")) {
            jsonObj.put(Const.EMOTICON_IMAGE_URL, m.getEmoticonImageUrl());
        }

        resultOfCouchDB = ConnectionHandler.postJsonObject(Const.SEND_MESSAGE_TO_USER, jsonObj, UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());
            
        if (resultOfCouchDB == null) {
            isSuccess = false;
        }
        return isSuccess;
    }

    public static void sendMessageToUserAsync(Message m, boolean isPut, ResultListener<Boolean> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Boolean>(new SendMessageToUser(m), resultListener, context, showProgressBar).execute();
    }
    
    private static class SendMessageToUser implements Command<Boolean> {
    	
    	Message m;
    	
    	public SendMessageToUser(Message m) {
			this.m = m;
		}

		@Override
		public Boolean execute() throws JSONException, IOException,
				SpikaException {
			return sendMessageToUser(m);
		}
    }
    
    /**
     * Update message for a user
     * 
     * @param m
     * @return
     */
    //TODO: NEW API ????
//    public static boolean updateMessageForUser(Message m) {
//    	
//    	Log.d("log", "couchDB: "+m.getImageThumbFileId());
//
//        boolean isSuccess = true;
//
//        JSONObject jsonObj = new JSONObject();
//
//        try {
//            jsonObj.put(Const._ID, m.getId());
//            jsonObj.put(Const._REV, m.getRev());
//            jsonObj.put(Const.MESSAGE_TYPE, m.getMessageType());
//            jsonObj.put(Const.MODIFIED, m.getModified());
//            jsonObj.put(Const.TYPE, m.getType());
//            jsonObj.put(Const.FROM_USER_NAME, m.getFromUserName());
//            jsonObj.put(Const.FROM_USER_ID, m.getFromUserId());
//            jsonObj.put(Const.VALID, m.isValid());
//            jsonObj.put(Const.MESSAGE_TARGET_TYPE, m.getMessageTargetType());
//            jsonObj.put(Const.CREATED, m.getCreated());
//            jsonObj.put(Const.TO_USER_NAME, m.getToUserName());
//            jsonObj.put(Const.TO_USER_ID, m.getToUserId());
//            jsonObj.put(Const.BODY, m.getBody());
//            if (!m.getLatitude().equals("")) {
//                jsonObj.put(Const.LATITUDE, m.getLatitude());
//            }
//            if (!m.getLongitude().equals("")) {
//                jsonObj.put(Const.LONGITUDE, m.getLongitude());
//            }
//            if (!m.getAttachments().equals("")) {
//                jsonObj.put(Const._ATTACHMENTS, new JSONObject(m.getAttachments()));
//            }
//            if (!m.getVideoFileId().equals("")) {
//                jsonObj.put(Const.VIDEO_FILE_ID, m.getVideoFileId());
//            }
//            if (!m.getVoiceFileId().equals("")) {
//                jsonObj.put(Const.VOICE_FILE_ID, m.getVoiceFileId());
//            }
//            if (!m.getImageFileId().equals("")) {
//                jsonObj.put(Const.PICTURE_FILE_ID, m.getImageFileId());
//            }
//            if (!m.getImageThumbFileId().equals("")) {
//                jsonObj.put(Const.PICTURE_THUMB_FILE_ID, m.getImageThumbFileId());
//            }
//            if (!m.getEmoticonImageUrl().equals("")) {
//                jsonObj.put(Const.EMOTICON_IMAGE_URL, m.getEmoticonImageUrl());
//            }
//            
//        } catch (JSONException e) {
//            e.printStackTrace();
//            isSuccess = false;
//        }
//
//        // XXX Need to check if json return ok or failed like for delete group
//        JSONObject resultOfCouchDB = ConnectionHandler.putJsonObject(jsonObj, m.getId(),
//                UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());
//
//        if (resultOfCouchDB == null) {
//            isSuccess = false;
//        }
//        return isSuccess;
//    }
   
//************* SEND MESSAGE TO GROUP ********************
    
    /**
     * Send message to a group
     * 
     * @param m
     * @param isPut
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws IllegalStateException 
     * @throws ClientProtocolException 
     */
    public static boolean sendMessageToGroup(Message m) throws ClientProtocolException, IllegalStateException, IOException, JSONException, SpikaException {

        boolean isSuccess = true;

        JSONObject resultOfCouchDB = null;
        
        JSONObject jsonObj = new JSONObject();
        
        jsonObj.put(Const.MESSAGE_TYPE, m.getMessageType());
        jsonObj.put(Const.MODIFIED, m.getModified());
        jsonObj.put(Const.TYPE, m.getType());
        jsonObj.put(Const.FROM_USER_NAME, m.getFromUserName());
        jsonObj.put(Const.FROM_USER_ID, m.getFromUserId());
        jsonObj.put(Const.VALID, m.isValid());
        jsonObj.put(Const.TO_GROUP_ID, m.getToGroupId());
        jsonObj.put(Const.TO_GROUP_NAME, m.getToGroupName());
        jsonObj.put(Const.MESSAGE_TARGET_TYPE, m.getMessageTargetType());
        jsonObj.put(Const.CREATED, m.getCreated());
        jsonObj.put(Const.BODY, m.getBody());

        if (!m.getLatitude().equals("")) {
            jsonObj.put(Const.LATITUDE, m.getLatitude());
        }
        if (!m.getLongitude().equals("")) {
            jsonObj.put(Const.LONGITUDE, m.getLongitude());
        }
        if (!m.getAttachments().equals("")) {
            jsonObj.put(Const._ATTACHMENTS, new JSONObject(m.getAttachments()));
        }
        if (!m.getVideoFileId().equals("")) {
            jsonObj.put(Const.VIDEO_FILE_ID, m.getVideoFileId());
        }
        if (!m.getVoiceFileId().equals("")) {
            jsonObj.put(Const.VOICE_FILE_ID, m.getVoiceFileId());
        }
        if (!m.getImageFileId().equals("")) {
            jsonObj.put(Const.PICTURE_FILE_ID, m.getImageFileId());
        }
        if (!m.getImageThumbFileId().equals("")) {
            jsonObj.put(Const.PICTURE_THUMB_FILE_ID, m.getImageFileId());
        }
        if (!m.getEmoticonImageUrl().equals("")) {
            jsonObj.put(Const.EMOTICON_IMAGE_URL, m.getEmoticonImageUrl());
        }
        
        resultOfCouchDB = ConnectionHandler.postJsonObject(Const.SEND_MESSAGE_TO_GROUP, jsonObj, UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());

        if (resultOfCouchDB == null) {
            isSuccess = false;
        }
        return isSuccess;
    }
    
    public static void sendMessageToGroupAsync(Message m, boolean isPut, ResultListener<Boolean> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Boolean>(new SendMessageToGroup(m), resultListener, context, showProgressBar).execute();
    }
    
    private static class SendMessageToGroup implements Command<Boolean> {
    	
    	Message m;
    	
    	public SendMessageToGroup(Message m) {
    		this.m = m;
    	}

		@Override
		public Boolean execute() throws JSONException, IOException,
				SpikaException {
			return sendMessageToGroup(m);
		}
    }

    
    //TODO: NEW API ????
//    public static boolean updateMessageForGroup(Message m) {
//        boolean isSuccess = true;
//
//        JSONObject jsonObj = new JSONObject();
//        try {
//            jsonObj.put(Const._ID, m.getId());
//            jsonObj.put(Const._REV, m.getRev());
//            jsonObj.put(Const.MESSAGE_TYPE, m.getMessageType());
//            jsonObj.put(Const.MODIFIED, m.getModified());
//            jsonObj.put(Const.TYPE, m.getType());
//            jsonObj.put(Const.FROM_USER_NAME, m.getFromUserName());
//            jsonObj.put(Const.FROM_USER_ID, m.getFromUserId());
//            jsonObj.put(Const.VALID, m.isValid());
//            jsonObj.put(Const.TO_GROUP_ID, m.getToGroupId());
//            jsonObj.put(Const.TO_GROUP_NAME, m.getToGroupName());
//            jsonObj.put(Const.MESSAGE_TARGET_TYPE, m.getMessageTargetType());
//            jsonObj.put(Const.CREATED, m.getCreated());
//            jsonObj.put(Const.BODY, m.getBody());
//
//            if (!m.getLatitude().equals("")) {
//                jsonObj.put(Const.LATITUDE, m.getLatitude());
//            }
//            if (!m.getLongitude().equals("")) {
//                jsonObj.put(Const.LONGITUDE, m.getLongitude());
//            }
//            if (!m.getAttachments().equals("")) {
//                jsonObj.put(Const._ATTACHMENTS, new JSONObject(m.getAttachments()));
//            }
//            if (!m.getVideoFileId().equals("")) {
//                jsonObj.put(Const.VIDEO_FILE_ID, m.getVideoFileId());
//            }
//            if (!m.getVoiceFileId().equals("")) {
//                jsonObj.put(Const.VOICE_FILE_ID, m.getVoiceFileId());
//            }
//            if (!m.getImageFileId().equals("")) {
//                jsonObj.put(Const.PICTURE_FILE_ID, m.getImageFileId());
//            }
//            if (!m.getImageThumbFileId().equals("")) {
//                jsonObj.put(Const.PICTURE_THUMB_FILE_ID, m.getImageThumbFileId());
//            }
//            if (!m.getEmoticonImageUrl().equals("")) {
//                jsonObj.put(Const.EMOTICON_IMAGE_URL, m.getEmoticonImageUrl());
//            }
//
//        } catch (JSONException e) {
//            e.printStackTrace();
//            isSuccess = false;
//        }
//
//        // XXX Need to check if json return ok or failed like for delete group
//
//        JSONObject resultOfCouchDB = ConnectionHandler.putJsonObject(jsonObj, m.getId(),
//                UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());
//
//        if (resultOfCouchDB == null) {
//            isSuccess = false;
//        }
//        return isSuccess;
//    }
 
    //****************** DELETE MESSAGE ****************************
    
    public static void deleteMessageAsync (String messageId, String deleteType, ResultListener<Void> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Void>(new DeleteMessage(messageId, deleteType), resultListener, context, showProgressBar).execute();
    }
    
    private static void deleteMessage (String messageId, String deleteType) throws JSONException, ClientProtocolException, IllegalStateException, IOException, SpikaException {
    	JSONObject jsonObj = new JSONObject();
        
        jsonObj.put(Const.MESSAGE_ID, messageId);
        jsonObj.put(Const.DELETE_TYPE, deleteType);
                
        ConnectionHandler.postJsonObjectForString(Const.SET_DELETE, jsonObj, UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());
    }
    
    private static class DeleteMessage implements Command<Void> {

    	String messageId;
    	String deleteType;
    	
    	public DeleteMessage (String messageId, String deleteType) {
    		this.messageId = messageId;
    		this.deleteType = deleteType;
    	}
    	
		@Override
		public Void execute() throws JSONException, IOException, SpikaException {
			deleteMessage(messageId, deleteType);
			return null;
		}
    }
    
    
    
    
    //****************** CREATE COMMENT ****************************
    
    /**
     * Create new comment
     * 
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws IllegalStateException 
     * @throws ClientProtocolException 
     */
    public static String createComment(Comment comment) throws ClientProtocolException, IllegalStateException, IOException, JSONException, SpikaException {

        JSONObject commentJson = new JSONObject();

       	commentJson.put(Const.COMMENT, comment.getComment());
        commentJson.put(Const.USER_ID, comment.getUserId());
        commentJson.put(Const.USER_NAME, comment.getUserName());
        commentJson.put(Const.CREATED, comment.getCreated());
        commentJson.put(Const.MESSAGE_ID, comment.getMessageId());
        commentJson.put(Const.TYPE, Const.COMMENT);
        
		return CouchDBHelper.createComment(ConnectionHandler.postJsonObject(Const.SEND_COMMENT, commentJson,
			        UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken()));
    }
    
    public static void createCommentAsync(Comment comment, ResultListener<String> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, String>(new CreateComment(comment), resultListener, context, showProgressBar).execute();
    }
    
    private static class CreateComment implements Command<String>
    {
    	Comment comment;
    	
    	public CreateComment(Comment comment) {
			this.comment = comment;
		}

		@Override
		public String execute() throws JSONException, IOException,
				SpikaException {
			
			String commentId = CouchDB.createComment(comment);

			if (commentId != null) {
				if (WallActivity.gCurrentMessages != null) {
					WallActivity.gCurrentMessages.clear();
				}
				WallActivity.gIsRefreshUserProfile = true;
			}

			return commentId;
		}
    } 

//************ FIND COMMENTS BY MESSAGE ID *********
    
    /**
     * Find comments by message id
     * 
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static List<Comment> findCommentsByMessageId(String messageId) throws ClientProtocolException, IOException, JSONException, SpikaException {

        try {
            messageId = URLEncoder.encode(messageId, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
        
        JSONObject json = ConnectionHandler.getJsonObject(Const.FIND_COMMENTS_BY_MESSAGE_ID + messageId + "/30/0", UsersManagement.getLoginUser().getId());

        return CouchDBHelper.parseMultiCommentObjects(json);
    }
    
    public static void findCommentsByMessageIdAsync (String messageId, ResultListener<List<Comment>> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, List<Comment>>(new FindCommentsByMessageId(messageId), resultListener, context, showProgressBar).execute();
    }
    
    private static class FindCommentsByMessageId implements Command<List<Comment>> {
    	
    	String messageId;
    	
    	public FindCommentsByMessageId (String messageId)
    	{
    		this.messageId = messageId;
    	}

		@Override
		public List<Comment> execute() throws JSONException, IOException,
				SpikaException {
			return findCommentsByMessageId(messageId);
		}
    }

    /**
     * Get comment count using reduce function
     * 
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static int getCommentCount(String messageId) throws ClientProtocolException, IOException, JSONException, SpikaException {

        try {
            messageId = URLEncoder.encode(messageId, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return 0;
        }

        JSONObject json = ConnectionHandler.getJsonObject(Const.COMMENTS_COUNT + messageId, UsersManagement.getLoginUser().getId());
        
        return CouchDBHelper.getCommentCount(json);
    }

    public static String getAuthUrl() {
        return sAuthUrl;
    }

    public static void setAuthUrl(String authUrl) {
        CouchDB.sAuthUrl = authUrl;
    }

    private static void sendPassword(String email) throws ClientProtocolException, IllegalStateException, IOException, SpikaException, JSONException {

    	final String URL = Const.PASSWORDREMINDER_URL + "email=" + email;
        ConnectionHandler.getString(URL, null);
    }
    
    public static void sendPassword(String email, ResultListener<Void> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, Void>(new CouchDB.SendPassword(email), resultListener, context, showProgressBar).execute();
    }
    
    private static class SendPassword implements Command<Void>
    {
    	String email;
    	
    	public SendPassword (String email)
    	{
    		this.email = email; 
    	}

		@Override
		public Void execute() throws JSONException, IOException, SpikaException {
			
			sendPassword(email);
			return null;
		}
    }
 
//*************** CREATE WACHING GROUP LOG ***************************
    /**
     * Create watching group log
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws IllegalStateException 
     * @throws ClientProtocolException 
     * 
     */
    public static String createWatchingGroupLog(WatchingGroupLog watchingGroupLog) throws ClientProtocolException, IllegalStateException, IOException, JSONException, SpikaException {

        JSONObject jsonObj = new JSONObject();

        jsonObj.put(Const.TYPE, Const.WATCHING_GROUP_LOG);
        jsonObj.put(Const.USER_ID, watchingGroupLog.getUserId());
        jsonObj.put(Const.GROUP_ID, watchingGroupLog.getGroupId());
        jsonObj.put(Const.CREATED, Utils.getCurrentDateTime());
        
        String result = ConnectionHandler.postJsonObjectForString(Const.WATCH_GROUP, jsonObj, UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken());
        
        return CouchDBHelper.createWatchingGroupLog(result);
    }
    
    public static void createWatchingGroupLogAsync (WatchingGroupLog watchingGroupLog, ResultListener<String> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, String>(new CreateWatchingGroupLog (watchingGroupLog), resultListener, context, showProgressBar).execute();
    }
    
    private static class CreateWatchingGroupLog implements Command<String> {
    	
    	WatchingGroupLog watchingGroupLog;
    	
    	public CreateWatchingGroupLog (WatchingGroupLog watchingGroupLog)
    	{
    		this.watchingGroupLog = watchingGroupLog;
    	}

		@Override
		public String execute() throws JSONException, IOException,
				SpikaException {
			return createWatchingGroupLog(watchingGroupLog);
		}
    }

    
//*************** DELETE WACHING GROUP LOG ***************************
    /**
     * Delete watching group log
     * @throws JSONException 
     * @throws SpikaException 
     * @throws IOException 
     * @throws IllegalStateException 
     * @throws ClientProtocolException 
     * 
     */
    public static String deleteWatchingGroupLog(String id, String rev) throws JSONException, ClientProtocolException, IllegalStateException, IOException, SpikaException {

    	JSONObject create = new JSONObject();
    	create.put(Const.GROUP_ID, id);
    	create.put(Const.REV, rev);
    	
    	return CouchDBHelper.deleteWatchingGroupLog(ConnectionHandler.postJsonObjectForString(Const.UNWATCH_GROUP, create, UsersManagement.getLoginUser().getId(), UsersManagement.getLoginUser().getToken()));
    }
    
    public static void deleteWatchingGroupLogAsync(String id, String rev, ResultListener<String> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, String>(new DeleteWatchingGroupLog(id, rev), resultListener, context, showProgressBar).execute();
    }
    
    private static class DeleteWatchingGroupLog implements Command<String> {
    	
    	String id;
    	String rev;
    	
    	public DeleteWatchingGroupLog(String id, String rev)
    	{
    		this.id = id;
    		this.rev = rev;
    	}

		@Override
		public String execute() throws JSONException, IOException,
				SpikaException {
			return deleteWatchingGroupLog(id, rev);
		}
    }

    
// *********** GET BITMAP OBJECT ********************
    
//    /**
//     * Get a bitmap object
//     * 
//     * @param url
//     * @return
//     */
//    @Deprecated
//    public static Bitmap getBitmapObject(String url) {
//
//        return ConnectionHandler.getBitmapObject(url, UsersManagement.getLoginUser().getId(),
//                UsersManagement.getLoginUser().getToken());
//    }
//    
//    public static void getBitmapObjectAsync(String url, ResultListener<Bitmap> resultListener, Context context, boolean showProgressBar) {
//    	new SpikaAsyncTask<Void, Void, Bitmap>(new GetBitmapObject(url), resultListener, context, showProgressBar).execute();
//    }
//    
//    public static class GetBitmapObject implements Command<Bitmap>{
//    	
//    	String url;
//    	
//    	public GetBitmapObject (String url)
//    	{
//    		this.url = url;
//    	}
//
//		@Override
//		public Bitmap execute() throws JSONException, IOException {
//			return getBitmapObject(url);
//		}
//    }
    
//************** FIND ALL EMOTICONS ***************************
    
    /**
     * Get a list of emoticons from database
     * 
     * @return
     * @throws SpikaException 
     * @throws JSONException 
     * @throws IOException 
     * @throws ClientProtocolException 
     */
    public static List<Emoticon> findAllEmoticons() throws ClientProtocolException, IOException, JSONException, SpikaException {
        JSONObject json = ConnectionHandler.getJsonObject(Const.FIND_ALL_EMOTICONS, UsersManagement.getLoginUser().getId());
        return CouchDBHelper.parseMultiEmoticonObjects(json);
    }
    
    public static void findAllEmoticonsAsync(ResultListener<List<Emoticon>> resultListener, Context context, boolean showProgressBar) {
    	new SpikaAsyncTask<Void, Void, List<Emoticon>>(new FindAllEmoticons(), resultListener, context, showProgressBar).execute();
    }
    
    public static class FindAllEmoticons implements Command<List<Emoticon>>
    {
		@Override
		public List<Emoticon> execute() throws JSONException, IOException, SpikaException {
			if (Emoticons.getInstance().getEmoticons() == null) {
				return CouchDB.findAllEmoticons();
			} else {
				return Emoticons.getInstance().getEmoticons();
			}
		}
    }
}
