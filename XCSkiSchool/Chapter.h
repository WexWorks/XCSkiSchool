//
//  Chapter.h
//  XCSkiSchool
//
//  Created by Daniel Wexler on 7/6/12.
//  Copyright (c) 2012 The 11ers. All rights reserved.
//

#import <Foundation/Foundation.h>

@class S3GetObjectRequest;

UIImage *chapterPlayImage;
UIImage *chapterDownloadImage;
UIImage *chapterPurchaseImage;

@interface Chapter : NSObject {
  @public
  BOOL isCached;
  BOOL isOwned;
  BOOL isDownloading;
  long long totalBytes;
  long long curBytes;
  S3GetObjectRequest *request;
}

@property (retain, nonatomic) NSString *name;
@property (retain, nonatomic) NSString *file;
@property (retain, nonatomic) NSString *path;
@property (retain, nonatomic) NSString *sku;

- (Chapter *)initWithTitle:(NSString *)title;
- (void)updateUIState:(UIImageView *)stateImageView progress:(UIProgressView *)progressView;

@end
