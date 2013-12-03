//
//  ViewController.h
//  XCSkiSchool
//
//  Created by Daniel Wexler on 7/6/12.
//  Copyright (c) 2012 The 11ers. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <AWSiOSSDK/S3/AmazonS3Client.h>
#import "AmazonTVMClient.h"
#import  "mediaplayer/MPMoviePlayerViewController.h"

@class Reachability;

@interface ViewController : UITableViewController <AmazonServiceRequestDelegate> {
  NSMutableArray *chapterArray;
  NSOperationQueue *downloadQueue;
  AmazonS3Client *s3;
  AmazonTVMClient *tvm;
  MPMoviePlayerViewController *moviePlayer;
  Reachability *internetReachable;
  Reachability *hostReachable;
  BOOL internetActive;
  BOOL hostActive;
}

@property (retain, nonatomic) IBOutlet UITableView *videoTableView;

-(void) checkNetworkStatus:(NSNotification *)notice;

@end
