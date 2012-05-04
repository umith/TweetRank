package graph;

import com.larvalabs.megamap.MegaMapManager;
import com.larvalabs.megamap.MegaMap;
import com.larvalabs.megamap.MegaMapException;
import java.io.*;
import java.util.*;

public class MegaGraph {
	/** Contains all tweets */
	private Hashtable<Long,Long> tweetSet;
	private ArrayList<Long> tweetList;

	/** Maps users to tweets */
	private MegaMap userTweets;

	/** Maps a tweet to a list of user mentions */
	private MegaMap mentioned;

	/** Maps a user to a list of users he/she follows */
	private MegaMap follows;

	/** Map a reply/retweet to the original tweet */
	private MegaMap refTweets;

	/** Map a tweet to a list of hashtags */
	private MegaMap hashtagsByTweet;
	private MegaMap tweetsByHashtag;

	private MegaMapManager manager;
	private MegaGraph oldGraph;

	private String name;
	private String path;

	/** Private constructor, needed to handle exceptions in the construction method. */
	private MegaGraph() { }

	/** Stores the objects that are not handled by MegaMap. */
	public void saveTweets() {
		try {
			FileOutputStream fTweetSet   = new FileOutputStream(path + "/" + name + "__TweetSet");
			ObjectOutputStream oTweetSet = new ObjectOutputStream(fTweetSet);
			oTweetSet.writeObject(tweetSet);
			oTweetSet.close();
		} catch (IOException e) {
			// Ignored exception
		}
	}

	@SuppressWarnings("unchecked")
	public void loadOrCreateTweets() {
		try {
			FileInputStream fTweetSet   = new FileInputStream(path + "/" + name + "__TweetSet");
			ObjectInputStream oTweetSet = new ObjectInputStream(fTweetSet);
			tweetSet = (Hashtable<Long,Long>)oTweetSet.readObject();
		} catch ( Exception e ) {
			tweetSet = new Hashtable<Long,Long>();
		}

		tweetList = new ArrayList<Long>();
		tweetList.addAll(tweetSet.keySet());
	}


	/** Constructor. The oldGraph is set to null. */
	public static MegaGraph createMegaGraph(String name, String path, MegaMapManager manager) throws MegaMapException {
		MegaGraph mg  = new MegaGraph();
		mg.manager    = manager;
		mg.oldGraph   = null;
		mg.name       = name;
		mg.path       = path;
		mg.loadOrCreateTweets();
		mg.mentioned  = mg.manager.createMegaMap(name + "__Mention", path, true, false);
		mg.follows    = mg.manager.createMegaMap(name + "__Follows", path, true, false);
		mg.refTweets  = mg.manager.createMegaMap(name + "__RefTweets", path, true, false);
		mg.userTweets = mg.manager.createMegaMap(name + "__userTweets", path, true, false);
		mg.hashtagsByTweet = mg.manager.createMegaMap(name + "__HashtagsByTweet", path, true, false);
		mg.tweetsByHashtag = mg.manager.createMegaMap(name + "__TweetsByHashtag", path, true, false);
		return mg;
	}
	
	private static void copyMegaMap(MegaMap src, MegaMap dst) throws MegaMapException {
		@SuppressWarnings("unchecked")
		Set<Serializable> keys = src.getKeys();
		for (Serializable key : keys) {
			Serializable value = src.get(key);
			dst.put(key, value);
		}
	}
	
	private static void removeFile(String filename) {
		File f = new File(filename);
		if (f.exists() && f.isFile()) f.delete();
	}

	/** Copy the current graph to a new file. */
	public MegaGraph copy(String name) throws MegaMapException {
		MegaGraph mg = new MegaGraph();
		mg.manager    = this.manager;
		mg.oldGraph   = this.oldGraph;
		mg.path       = this.path;
		mg.name       = name;
		mg.loadOrCreateTweets();
		mg.mentioned  = mg.manager.createMegaMap(name + "__Mention", mg.path, true, false);
		mg.follows    = mg.manager.createMegaMap(name + "__Follows", mg.path, true, false);
		mg.refTweets  = mg.manager.createMegaMap(name + "__RefTweets", mg.path, true, false);
		mg.userTweets = mg.manager.createMegaMap(name + "__userTweets", mg.path, true, false);
		mg.hashtagsByTweet = mg.manager.createMegaMap(name + "__HashtagsByTweet", mg.path, true, false);
		mg.tweetsByHashtag = mg.manager.createMegaMap(name + "__TweetsByHashtag", mg.path, true, false);
		
		// Copy tweetSet and tweetList...
		mg.tweetSet.putAll(tweetSet);
		mg.tweetList.addAll(tweetList);

		// Copy MegaMaps...
		copyMegaMap(mentioned, mg.mentioned);
		copyMegaMap(follows, mg.follows);
		copyMegaMap(refTweets, mg.refTweets);
		copyMegaMap(userTweets, mg.userTweets);
		copyMegaMap(hashtagsByTweet, mg.hashtagsByTweet);
		copyMegaMap(tweetsByHashtag, mg.tweetsByHashtag);

		return mg;
	}

	/** Delete the files of the current graph. The graph MUST NOT BE USED AGAIN. */
	public void delete() throws MegaMapException {
		// Remove references to MegaMaps
		manager.removeMegaMap(name + "__Mention");
		manager.removeMegaMap(name + "__Follows");
		manager.removeMegaMap(name + "__RefTweets");
		manager.removeMegaMap(name + "__UserTweets");
		manager.removeMegaMap(name + "__HashtagsByTweet");
		manager.removeMegaMap(name + "__TweetsByHashtag");
		
		// Delete physical files
		removeFile(path + "/" + name + "__TweetSet");
		removeFile(path + "/" + name + "__TweetList");
		manager.deletePersistedMegaMap(name + "__Mention", path);
		manager.deletePersistedMegaMap(name + "__Follows", path);
		manager.deletePersistedMegaMap(name + "__RefTweets", path);
		manager.deletePersistedMegaMap(name + "__UserTweets", path);
		manager.deletePersistedMegaMap(name + "__HashtagsByTweet", path);
		manager.deletePersistedMegaMap(name + "__TweetsByHashtag", path);
	}

	private void addTweet(Long tweetID, Long userID) {
		if ( !tweetSet.contains(tweetID) )
			tweetList.add(tweetID);

		// In case we add the userID later, we need to override the previous value in tweetSet
		if (tweetSet.get(tweetID) == null) 
			tweetSet.put(tweetID, userID);
	}

	private void addAllTweets(List<Long> tweetIDs, Long userID) {
		for (Long tid : tweetIDs)
			addTweet(tid, userID);
	}

	public void addRefTweets(Long tweetID, Long refTweetID) {
		addTweet(tweetID, null);
		addTweet(refTweetID, null);
		refTweets.put(tweetID, refTweetID);
	}

	public void addUserTweets(Long userID, List<Long> tweetIDs) throws MegaMapException {
		@SuppressWarnings("unchecked")
		ArrayList<Long> curr_list = (ArrayList<Long>)userTweets.get(userID);
		if (curr_list == null) curr_list = new ArrayList<Long>();
		curr_list.addAll(tweetIDs);
		addAllTweets(tweetIDs, userID);
		userTweets.put(userID, curr_list);
	}

	public void addMentioned(Long tweetID, List<Long> userIDs) throws MegaMapException {
		@SuppressWarnings("unchecked")
		ArrayList<Long> curr_list = (ArrayList<Long>)mentioned.get(tweetID);
		if (curr_list == null) curr_list = new ArrayList<Long>();
		curr_list.addAll(userIDs);
		addTweet(tweetID, null);
		mentioned.put(tweetID, curr_list);
	}

	public void addFollows(Long userID, List<Long> userIDs) throws MegaMapException {
		@SuppressWarnings("unchecked")
		ArrayList<Long> curr_list = (ArrayList<Long>)follows.get(userID);
		if (curr_list == null) curr_list = new ArrayList<Long>();
		curr_list.addAll(userIDs);
		follows.put(userID, curr_list);
	}

	public void addHashtags(Long tweetID, List<String> hashtags) throws MegaMapException {
		@SuppressWarnings("unchecked")
		ArrayList<String> curr_list = (ArrayList<String>)hashtagsByTweet.get(tweetID);
		if (curr_list == null) curr_list = new ArrayList<String>();
		curr_list.addAll(hashtags);
		hashtagsByTweet.put(tweetID, curr_list);

		// Transpose the list
		for(String ht : hashtags) {
			@SuppressWarnings("unchecked")
			ArrayList<Long> tweets = (ArrayList<Long>)tweetsByHashtag.get(ht);
			if (tweets == null) tweets = new ArrayList<Long>();
			tweets.add(tweetID);
			tweetsByHashtag.put(ht, tweets);
		}

		addTweet(tweetID, null);
	}

	public Long getRandomTweet(Random r) {
		return tweetList.get(r.nextInt(tweetList.size()));
	}
	
	public Long getRefTweet(Long tweetID) throws MegaMapException {
		if ( tweetID == null ) return null;
		return (Long)refTweets.get(tweetID);
	}	
	
	@SuppressWarnings("unchecked")
	public ArrayList<String> getHashtagsByTweet(Long tweetID) throws MegaMapException {
		if ( tweetID == null ) return null;
		return (ArrayList<String>)hashtagsByTweet.get(tweetID);
	}

	@SuppressWarnings("unchecked")
	public ArrayList<Long> getTweetsByHashtag(String hashtag) throws MegaMapException {
		if ( hashtag == null ) return null;
		return (ArrayList<Long>)tweetsByHashtag.get(hashtag);
	}	

	@SuppressWarnings("unchecked")
	public ArrayList<Long> getUserTweets(Long userID) throws MegaMapException {
		if ( userID == null ) return null;
		return (ArrayList<Long>)userTweets.get(userID);
	}

	@SuppressWarnings("unchecked")
	public ArrayList<Long> getMentionedUsers(Long tweetID) throws MegaMapException {
		if ( tweetID == null ) return null;
		return (ArrayList<Long>)mentioned.get(tweetID);
	}

	@SuppressWarnings("unchecked")
	public ArrayList<Long> getFollowingUsers(Long userID) throws MegaMapException {
		if ( userID == null ) return null;
		return (ArrayList<Long>)follows.get(userID);
	}

	public Long getTweetOwner(Long tweetID) {
		if ( tweetID == null ) return null;
		return tweetSet.get(tweetID);
	}
	
	public Set<Long> getTweetSet() {
		return tweetSet.keySet();
	}
	
	public int getNumberOfTweets() {
		return tweetSet.size();
	}
	
	public int getNumberOfUsers() {
		return userTweets.getKeys().size();
	}
	
	public int getNumberOfHashtags() {
		return tweetsByHashtag.getKeys().size();
	}
	
	@SuppressWarnings("unchecked")
	public double getAverageFriendsPerUser() throws MegaMapException {
		int Tfollowed = 0;
		for (Long uid : (Set<Long>)follows.getKeys())
			Tfollowed += ((ArrayList<Long>)follows.get(uid)).size();
		
		return Tfollowed/(double)getNumberOfUsers();
	}
	
	public double getAverageTweetsPerUser() {
		return getNumberOfTweets()/(double)getNumberOfUsers();
	}
	
	@SuppressWarnings("unchecked")
	public double getAverageMentionsPerTweet() throws MegaMapException {
		int Tmentions = 0;
		for (Long tid : (Set<Long>)mentioned.getKeys())
			Tmentions += ((ArrayList<Long>)mentioned.get(tid)).size();
		return Tmentions/(double)getNumberOfTweets();
	}
	
	public double getAverageReferencePerTweet() {
		return refTweets.getKeys().size()/(double)getNumberOfTweets();
	}
}