//
//  Chapter.m
//  XCSkiSchool
//
//  Created by Daniel Wexler on 7/6/12.
//  Copyright (c) 2012 The 11ers. All rights reserved.
//

#import "Chapter.h"

@implementation Chapter

@synthesize name;
@synthesize file;
@synthesize path;
@synthesize sku;


- (Chapter *)initWithTitle:(NSString *)title {
  name = title;
  [title retain];
  isCached = false;
  isOwned = true;
  isDownloading = false;
  sku = nil;

  NSString *cleanName = [title stringByReplacingOccurrencesOfString:@"?" withString:@""];
  NSString *camelCase = [cleanName capitalizedString];
  NSString *token = [camelCase stringByReplacingOccurrencesOfString:@" " withString:@""];
  file = [[token stringByAppendingString:@".m4v"] retain];
  
  NSArray *cacheArray = NSSearchPathForDirectoriesInDomains(NSCachesDirectory,
                                                       NSUserDomainMask, YES);
  NSString *cacheDirectory = [cacheArray objectAtIndex:0];
  path = [[cacheDirectory stringByAppendingPathComponent:file] retain];
  
  isCached = [[NSFileManager defaultManager] fileExistsAtPath:path];
  return self;
}


- (void) updateUIState:(UIImageView *)stateImageView progress:(UIProgressView *)progressView {
  if (isDownloading) {
    progressView.hidden = false;
    stateImageView.hidden = true;
    if (totalBytes > 0)
      progressView.progress = curBytes / (float)totalBytes;
  } else {
    progressView.hidden = true;
    stateImageView.hidden = false;
    if (isCached && isOwned)
      stateImageView.image = chapterPlayImage;
    else if (isOwned)
      stateImageView.image = chapterDownloadImage;
    else
      stateImageView.image = chapterPurchaseImage;    
  }
}

@end
