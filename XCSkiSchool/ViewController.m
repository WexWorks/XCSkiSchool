//
//  ViewController.m
//  XCSkiSchool
//
//  Created by Daniel Wexler on 7/6/12.
//  Copyright (c) 2012 The 11ers. All rights reserved.
//

#import "ViewController.h"
#import "Chapter.h"

#import "AmazonKeyChainWrapper.h"
#import "Response.h"
#import "Reachability.h"


#define MAIN_LABEL_TAG  1
#define STATE_IMAGE_TAG 2
#define PROGRESS_TAG    3


@interface ViewController ()

@end

@implementation ViewController
@synthesize videoTableView;


- (id)initWithStyle:(UITableViewStyle)style {
  self = [super initWithStyle:style];
  if (self) {
    // Custom initialization
  }
  return self;
}


- (void)viewDidLoad {
  [super viewDidLoad];
  
  chapterArray = nil;
  downloadQueue = nil;
  s3 = nil;
  tvm = nil;
  internetActive = NO;
  hostActive = NO;
  
  // Uncomment the following line to display an Edit button in the navigation bar for this view controller.
  // self.navigationItem.rightBarButtonItem = self.editButtonItem;
  
  // Preserve selection between presentations.
  self.clearsSelectionOnViewWillAppear = NO;
  
  // Clear the empty tables cells
  self.tableView.tableFooterView = [UIView new];

  NSString *filePath = [[NSBundle mainBundle] pathForResource:@"Chapter"
                                                       ofType:@"plist"];
  NSArray *array = [NSArray arrayWithContentsOfFile:filePath];
  
  bool isOneChapterCached = false;
  chapterArray = [[NSMutableArray alloc] init];
  for (NSString *title in array) {
    Chapter *chapter = [[Chapter alloc] initWithTitle:title];
    [chapterArray addObject:chapter];
    if (chapter->isCached)
      isOneChapterCached = true;
  }
  
  filePath = [[NSBundle mainBundle] pathForResource:@"ic_play" ofType:@"png"];
  chapterPlayImage = [[UIImage alloc] initWithContentsOfFile:filePath];

  filePath = [[NSBundle mainBundle] pathForResource:@"ic_download" ofType:@"png"];
  chapterDownloadImage = [[UIImage alloc] initWithContentsOfFile:filePath];

  filePath = [[NSBundle mainBundle] pathForResource:@"ic_purchase" ofType:@"png"];
  chapterPurchaseImage = [[UIImage alloc] initWithContentsOfFile:filePath];
  
  downloadQueue = [[NSOperationQueue alloc] init];
  
  // check for internet connection
  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(checkNetworkStatus:) name:kReachabilityChangedNotification object:nil];
  
  internetReachable = [[Reachability reachabilityForInternetConnection] retain];
  [internetReachable startNotifier];
  
  // check if a pathway to a random host exists
  hostReachable = [[Reachability reachabilityWithHostName: @"www.apple.com"] retain];
  [hostReachable startNotifier];
  
  // now patiently wait for the notification
  
  if (!isOneChapterCached) {
    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Download A Chapter"
                                                    message:@"Select one or more chapters to download before watching."
                                                   delegate:nil
                                          cancelButtonTitle:@"OK"
                                          otherButtonTitles:nil];
    [alert show];
    [alert release];
  }
}


- (void)viewDidUnload {
  [self setVideoTableView:nil];
  [super viewDidUnload];
  // Release any retained subviews of the main view.
  // e.g. self.myOutlet = nil;
}


- (BOOL)shouldAutorotateToInterfaceOrientation:(UIInterfaceOrientation)interfaceOrientation {
  return YES;
}


#pragma mark - Table view data source

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
  return 1;
}


- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
  return chapterArray.count;
}


- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
  static NSString *CellIdentifier = @"ChapterCell";
  UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:CellIdentifier];
  
  UILabel *mainLabel;
  UIImageView *stateImageView;
  UIProgressView *progressView;
  
  if (cell == nil) {
    cell = [[[UITableViewCell alloc] initWithStyle:UITableViewCellStyleDefault reuseIdentifier:CellIdentifier] autorelease];
    cell.contentView.autoresizingMask = UIViewAutoresizingFlexibleWidth;
    cell.contentView.autoresizesSubviews = YES;
    
    mainLabel = [[[UILabel alloc] initWithFrame:CGRectMake(4, 4, 220, 32)] autorelease];
    mainLabel.tag = MAIN_LABEL_TAG;
    mainLabel.font = [UIFont systemFontOfSize:18.0];
    mainLabel.autoresizingMask = UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth;
    [cell.contentView addSubview:mainLabel];

    UIView *rightAlignView = [[UIView alloc] initWithFrame:cell.frame];
    rightAlignView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    rightAlignView.contentMode = UIViewContentModeRight;
    [cell.contentView addSubview:rightAlignView];
    
    stateImageView = [[[UIImageView alloc] initWithFrame:CGRectMake(0, 0, 32, 32)] autorelease];
    stateImageView.tag = STATE_IMAGE_TAG;
    stateImageView.autoresizingMask = UIViewAutoresizingFlexibleLeftMargin;
    stateImageView.center = CGPointMake(rightAlignView.frame.size.width-32, rightAlignView.frame.size.height / 2);
    [rightAlignView addSubview:stateImageView];
    
    progressView = [[[UIProgressView alloc] initWithProgressViewStyle:UIProgressViewStyleDefault] autorelease];
    progressView.tag = PROGRESS_TAG;
    progressView.frame = CGRectMake(0, 0, 64, 32);
    progressView.autoresizingMask = UIViewAutoresizingFlexibleLeftMargin;
    progressView.center = CGPointMake(rightAlignView.frame.size.width-64, rightAlignView.frame.size.height / 2);
    [rightAlignView addSubview:progressView];
  } else {
    mainLabel = (UILabel *)[cell.contentView viewWithTag:MAIN_LABEL_TAG];
    stateImageView = (UIImageView *)[cell.contentView viewWithTag:STATE_IMAGE_TAG];
    progressView = (UIProgressView *)[cell.contentView viewWithTag:PROGRESS_TAG];
  }
  
  Chapter *chapter = [chapterArray objectAtIndex:indexPath.row];
  mainLabel.text = chapter.name;
  [chapter updateUIState:stateImageView progress:progressView];
  
  return cell;
}


#pragma mark - Table view delegate

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
  Chapter *chapter = [chapterArray objectAtIndex:indexPath.row];
  if (chapter == nil)
    return;
  if (chapter->isCached && chapter->isOwned) {
    NSLog(@"Play \"%@\"\n", chapter.name);
    NSURL *url = [[NSURL alloc] initFileURLWithPath:chapter.path];
    moviePlayer = [[MPMoviePlayerViewController alloc] initWithContentURL:url];
    [self presentMoviePlayerViewControllerAnimated:moviePlayer];
  } else if (chapter->isOwned) {
    [self downloadChapter:chapter];
//    NSInvocationOperation *op = [[[NSInvocationOperation alloc] initWithTarget:self selector:@selector(downloadChapter:) object:chapter] autorelease];
//    [downloadQueue addOperation:op];
  } else {
    NSLog(@"Purchase \"%@\"\n", chapter.name);
  }
}


#pragma mark - AmazonServiceRequestDelegate

- (Chapter *)findChapter:(AmazonServiceRequest *)request {
  Chapter *chapter = nil;
  for (int i = 0; i < chapterArray.count; ++i) {
    chapter = [chapterArray objectAtIndex:i];
    if (chapter->request == request)
      break;
  }
  if (chapter->request == request)
    return chapter;
  return nil;
}

- (void)request:(AmazonServiceRequest *)request didReceiveResponse:(NSURLResponse *)response {
  NSLog(@"Did received response, file size = %lld\n", response.expectedContentLength);
  Chapter *chapter = [self findChapter:request];
  if (chapter != nil) {
    chapter->totalBytes = response.expectedContentLength;
  }
}


- (void)request:(AmazonServiceRequest *)request didReceiveData:(NSData *)data {
  Chapter *chapter = [self findChapter:request];
  if (chapter != nil) {
    chapter->curBytes += data.length;
    [videoTableView reloadData];
  }
}


- (void)request:(AmazonServiceRequest *)request didCompleteWithResponse:(AmazonServiceResponse *)response {
  NSLog(@"Did complete with response\n");
  
  Chapter *chapter = [self findChapter:request];
  if (chapter != nil) {
    chapter->isDownloading = false;
    NSLog(@"Saving to \"%@\"\n", chapter.path);
    NSError *err = nil;
    if ([response.body writeToFile:chapter.path options:0 error:&err]) {
      chapter->isCached = true;
    } else {
      if (err != nil) {
        NSLog(@"Error saving file \"%@\"\n", chapter.path);
      }
    }
    [videoTableView reloadData];
  }
}


- (void)request:(AmazonServiceRequest *)request didSendData:(NSInteger)bytesWritten totalBytesWritten:(NSInteger)totalBytesWritten totalBytesExpectedToWrite:(NSInteger)totalBytesExpectedToWrite {
  NSLog(@"Did send data\n");
}


- (void)request:(AmazonServiceRequest *)request didFailWithError:(NSError *)error {
  NSLog(@"Did fail with error\n");
  Chapter *chapter = [self findChapter:request];
  if (chapter != nil) {
    chapter->isDownloading = false;
    [videoTableView reloadData];
  }
}


- (void)request:(AmazonServiceRequest *)request didFailWithServiceException:(NSException *)exception {
  NSLog(@"Did fail with service exception\n");
  Chapter *chapter = [self findChapter:request];
  if (chapter != nil) {
    chapter->isDownloading = false;
    [videoTableView reloadData];
  }
}


- (void)downloadChapter:(Chapter *)chapter {
  NSLog(@"Download \"%@\"\n", chapter.name);
  
  if (!self->internetActive || !self->hostActive) {
    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"No Network"
                                                    message:@"You must have a working internet connection to download a chapter."
                                                   delegate:nil
                                          cancelButtonTitle:@"OK"
                                          otherButtonTitles:nil];
    [alert show];
    [alert release];
    return;
  }
  
  if (tvm == nil) {
    tvm = [[AmazonTVMClient alloc] initWithEndpoint:@"http://xsstvm.elasticbeanstalk.com/" useSSL:NO];
  }
  
  Response *tvmResponse = [[[Response alloc] initWithCode:200 andMessage:@"OK"] autorelease];
  
  if ([AmazonKeyChainWrapper areCredentialsExpired]) {
    [s3 release];
    s3 = nil;
    
    tvmResponse = [tvm anonymousRegister];
    if ( [tvmResponse wasSuccessful]) {
      tvmResponse = [tvm getToken];
    }
  }
  
  if (![tvmResponse wasSuccessful]) {
    // Display credential alert!
    return;
  }
  
  if (s3 == nil) {
    AmazonCredentials *credentials = [AmazonKeyChainWrapper getCredentialsFromKeyChain];
    s3  = [[AmazonS3Client alloc] initWithCredentials:credentials];    
  }
  
  if (s3 == nil) {
    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Server Down"
                                                    message:@"The connection to the chapter library is not working. Please try again later."
                                                   delegate:nil
                                          cancelButtonTitle:@"OK"
                                          otherButtonTitles:nil];
    [alert show];
    [alert release];
    return;
  }
  
  chapter->isDownloading = true;
  chapter->totalBytes = 0;
  chapter->curBytes = 0;

  NSLog(@"Starting download of \"%@\"...\n", chapter.name);
  
  NSString *bucket = @"wwnordicskischool";
  chapter->request = [[[S3GetObjectRequest alloc] initWithKey:chapter.file withBucket:bucket] retain];
  [chapter->request setDelegate:self];
  [s3 getObject:chapter->request];
  [videoTableView reloadData];
}


#pragma mark - NetworkReachability


-(void) checkNetworkStatus:(NSNotification *)notice {
  // called after network status changes
  NetworkStatus internetStatus = [internetReachable currentReachabilityStatus];
  switch (internetStatus) {
    case NotReachable:
      NSLog(@"The internet is down.");
      self->internetActive = NO;
      break;
    case ReachableViaWiFi:
      NSLog(@"The internet is working via WIFI.");
      self->internetActive = YES;
      break;
    case ReachableViaWWAN:
      NSLog(@"The internet is working via WWAN.");
      self->internetActive = YES;
      break;
  }
  
  NetworkStatus hostStatus = [hostReachable currentReachabilityStatus];
  switch (hostStatus) {
    case NotReachable:
      NSLog(@"A gateway to the host server is down.");
      self->hostActive = NO;
      break;
    case ReachableViaWiFi:
      NSLog(@"A gateway to the host server is working via WIFI.");
      self->hostActive = YES;
      break;
    case ReachableViaWWAN:
      NSLog(@"A gateway to the host server is working via WWAN.");
      self->hostActive = YES;
      break;
  }
}


- (void)dealloc {
    [videoTableView release];
    [super dealloc];
}

@end
