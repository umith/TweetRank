#!/usr/bin/env python2.7
# -*- coding: utf-8 -*-

from RankerNotifier import RankerNotifier
from SolrNotifier import SolrNotifier
from Tweet import Tweet
from os import listdir
from xml.dom.minidom import parse
from threading import Thread, Lock
from random import shuffle
import sys

plock = Lock()
def sync_print(msg):
    plock.acquire()
    print msg
    plock.release()

class Worker(Thread):
    def __init__(self, nid, nthrs, ludir, ltdir, users_dir, tweets_dir):
        Thread.__init__(self)
        self.nid  = nid         # Thread ID
        self.nthrs = nthrs      # Number of workers
        self.ludir = ludir      # Users files
        self.ltdir = ltdir      # Tweets files
        self.udir = users_dir   # Users dir path
        self.tdir = tweets_dir  # Tweets dir path
        self.rnotif = RankerNotifier()    # Ranker notifier
        self.snotif = SolrNotifier()      # Solr notifier

    def run(self):
        for ifn in range(self.nid, len(self.ludir), self.nthrs):
            fname = self.ludir[ifn]
            user_id = int(fname.split('.')[0])
            ftype   = fname.split('.')[1]
            fname = self.udir + fname
            sync_print('Thread %d -> User file: %s' % (self.nid, fname))

            if ftype == 'friends':
                f = open(fname, 'r')
                friends = []
                for l in f:
                    friends.append(int(l))
                self.rnotif.add_user_friends(user_id, friends)
                f.close()
            else:
                pass # Add hashtags used by the user

        for ifn in range(self.nid, len(self.ltdir), self.nthrs):
            fname = self.tdir + self.ltdir[ifn]
            sync_print('Thread %d -> Tweets file: %s' % (self.nid, fname))

            dom = parse(fname)
            for tweet in dom.getElementsByTagName('status'):
                tweet = Tweet(tweet)
                self.snotif.notify_tweet(tweet) # Solr notification
                self.rnotif.notify_tweet(tweet) # Ranker notification

            self.snotif.flush()

def usage():
    print 'python DirectoryNotifier.py data_dir'

def main(argv):
    if len(argv) != 2:
        usage()
        return -1

    data_dir = argv[1]
    tweets_dir = data_dir + '/tweets/'
    users_dir  = data_dir + '/users/'

    fulist = listdir(users_dir)
    ftlist = listdir(tweets_dir)

    fulist.sort()
    ftlist.sort()

    nworkers = 16
    workers = [ Worker(tid, nworkers, fulist, ftlist, users_dir, tweets_dir) for tid in range(nworkers) ]
    for w in workers: w.start()
    for w in workers: w.join()

if __name__ == '__main__':
    sys.exit(main(sys.argv))
