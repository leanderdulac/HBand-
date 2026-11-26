// Proves the Veepoo-required MJExtension selectors exist and do not abort
// on a plain model. Runs on macOS (the Veepoo binary itself is iPhoneOS-only).
#import <Foundation/Foundation.h>
#import "MJExtension.h"

@interface N2UCrashProbeModel : NSObject
@property (nonatomic, copy) NSString *mac;
@property (nonatomic, copy) NSString *sleepTime;
@property (nonatomic, assign) int type;
@end
@implementation N2UCrashProbeModel
@end

static int failures = 0;
static void expect(BOOL cond, const char *msg) {
    if (!cond) {
        fprintf(stderr, "FAIL: %s\n", msg);
        failures++;
    }
}

int main(void) {
    @autoreleasepool {
        expect([N2UCrashProbeModel instancesRespondToSelector:@selector(mj_keyValues)],
               "mj_keyValues");
        expect([N2UCrashProbeModel instancesRespondToSelector:@selector(mj_keyValuesWithIgnoredKeys:)],
               "mj_keyValuesWithIgnoredKeys:");
        expect([N2UCrashProbeModel instancesRespondToSelector:@selector(mj_setKeyValues:)],
               "mj_setKeyValues:");
        expect([N2UCrashProbeModel respondsToSelector:@selector(mj_objectWithKeyValues:)],
               "mj_objectWithKeyValues:");
        expect([N2UCrashProbeModel respondsToSelector:@selector(mj_objectArrayWithKeyValuesArray:)],
               "mj_objectArrayWithKeyValuesArray:");
        expect([N2UCrashProbeModel respondsToSelector:@selector(mj_keyValuesArrayWithObjectArray:)],
               "mj_keyValuesArrayWithObjectArray:");

        N2UCrashProbeModel *model = [N2UCrashProbeModel new];
        model.mac = @"00:11:22:33:44:55";
        model.sleepTime = @"23:00";
        model.type = 1;

        id dict = nil;
        @try {
            dict = [model mj_keyValues];
        } @catch (NSException *ex) {
            fprintf(stderr, "FAIL: mj_keyValues threw %s\n", ex.name.UTF8String);
            failures++;
        }
        expect([dict isKindOfClass:[NSDictionary class]], "mj_keyValues returns a dictionary");
        expect([dict[@"mac"] isEqualToString:@"00:11:22:33:44:55"], "mj_keyValues keeps mac");

        id ignored = nil;
        @try {
            ignored = [model mj_keyValuesWithIgnoredKeys:@[@"type"]];
        } @catch (NSException *ex) {
            fprintf(stderr, "FAIL: mj_keyValuesWithIgnoredKeys: threw %s\n", ex.name.UTF8String);
            failures++;
        }
        expect([ignored isKindOfClass:[NSDictionary class]], "mj_keyValuesWithIgnoredKeys: returns a dictionary");
        expect(ignored[@"mac"] != nil, "ignored-keys path keeps mac");
        expect(ignored[@"type"] == nil, "ignored-keys path drops type");

        N2UCrashProbeModel *roundtrip = nil;
        @try {
            roundtrip = [N2UCrashProbeModel mj_objectWithKeyValues:@{
                @"mac": @"AA:BB",
                @"sleepTime": @"01:00",
                @"type": @2
            }];
        } @catch (NSException *ex) {
            fprintf(stderr, "FAIL: mj_objectWithKeyValues: threw %s\n", ex.name.UTF8String);
            failures++;
        }
        expect([roundtrip isKindOfClass:[N2UCrashProbeModel class]], "mj_objectWithKeyValues: returns the model");
        expect([roundtrip.mac isEqualToString:@"AA:BB"], "roundtrip mac");

        NSArray *arr = nil;
        @try {
            arr = [N2UCrashProbeModel mj_keyValuesArrayWithObjectArray:@[model]];
        } @catch (NSException *ex) {
            fprintf(stderr, "FAIL: mj_keyValuesArrayWithObjectArray: threw %s\n", ex.name.UTF8String);
            failures++;
        }
        expect(arr.count == 1, "array conversion count");

        @try {
            [model mj_setKeyValues:@{@"mac": @"CC:DD"}];
        } @catch (NSException *ex) {
            fprintf(stderr, "FAIL: mj_setKeyValues: threw %s\n", ex.name.UTF8String);
            failures++;
        }
        expect([model.mac isEqualToString:@"CC:DD"], "mj_setKeyValues: updates mac");
    }
    if (failures) {
        fprintf(stderr, "%d assertion(s) failed\n", failures);
        return 1;
    }
    puts("OK: MJExtension selectors used by Veepoo DBStoreManager do not abort");
    return 0;
}
