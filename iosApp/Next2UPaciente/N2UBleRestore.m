// Compilado com -fno-objc-arc (ver project.pbxproj): repassa init sem mexer em retain/release.
//
// O VeepooBleSDK cria seu próprio CBCentralManager sem restore id, mas o delegate
// (VPBleCentralManage) implementa centralManager:willRestoreState:. O iOS então registra
// "API MISUSE: ... has no restore identifier ... Restoring will not be supported".
// Aqui injetamos o restore id (e a fila principal) só nesse gerenciador do SDK, uma vez,
// para que o próprio gerenciador do SDK seja o restaurável (TN3115). VeepooSession consulta
// +injectedFlag e só cria um gerenciador próprio se a injeção não aconteceu.
#import <CoreBluetooth/CoreBluetooth.h>
#import <Foundation/Foundation.h>
#import <objc/runtime.h>

static NSString *const N2URestoreId = @"br.com.next2u.saude.paciente.ble";
static BOOL gInjected = NO;
static BOOL gDisabled = NO;
static id (*gOrigInit3)(id, SEL, id, dispatch_queue_t, NSDictionary *) = NULL;
static id (*gOrigInit2)(id, SEL, id, dispatch_queue_t) = NULL;

static BOOL N2UShouldInject(id delegate, NSDictionary *options) {
    if (gInjected || gDisabled || delegate == nil) return NO;
    if (options[CBCentralManagerOptionRestoreIdentifierKey] != nil) return NO;
    Class veepoo = NSClassFromString(@"VPBleCentralManage");
    return veepoo != Nil && [delegate isKindOfClass:veepoo];
}

static id N2UInit3(id self, SEL _cmd, id delegate, dispatch_queue_t queue, NSDictionary *options) {
    if (N2UShouldInject(delegate, options)) {
        gInjected = YES;
        NSMutableDictionary *opts = options ? [[options mutableCopy] autorelease] : [NSMutableDictionary dictionary];
        opts[CBCentralManagerOptionRestoreIdentifierKey] = N2URestoreId;
        opts[CBCentralManagerOptionShowPowerAlertKey] = @NO;
        NSLog(@"[Next2U] CBCentralManager do Veepoo criado com restore id.");
        return gOrigInit3(self, @selector(initWithDelegate:queue:options:), delegate, dispatch_get_main_queue(), opts);
    }
    return gOrigInit3(self, _cmd, delegate, queue, options);
}

static id N2UInit2(id self, SEL _cmd, id delegate, dispatch_queue_t queue) {
    if (N2UShouldInject(delegate, nil)) {
        return N2UInit3(self, @selector(initWithDelegate:queue:options:), delegate, queue, nil);
    }
    return gOrigInit2(self, _cmd, delegate, queue);
}

@interface N2UBleRestore : NSObject
@end

@implementation N2UBleRestore

+ (void)load {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        Class cls = [CBCentralManager class];
        SEL s3 = @selector(initWithDelegate:queue:options:);
        SEL s2 = @selector(initWithDelegate:queue:);
        Method m3 = class_getInstanceMethod(cls, s3);
        Method m2 = class_getInstanceMethod(cls, s2);
        if (m3 == NULL || m2 == NULL) return;
        IMP inherited3 = method_getImplementation(m3);
        IMP inherited2 = method_getImplementation(m2);
        // class_replaceMethod só altera CBCentralManager (não CBManager/CBPeripheralManager).
        IMP prev3 = class_replaceMethod(cls, s3, (IMP)N2UInit3, method_getTypeEncoding(m3));
        gOrigInit3 = (void *)(prev3 ?: inherited3);
        IMP prev2 = class_replaceMethod(cls, s2, (IMP)N2UInit2, method_getTypeEncoding(m2));
        gOrigInit2 = (void *)(prev2 ?: inherited2);
    });
}

+ (NSNumber *)injectedFlag {
    return @(gInjected);
}

+ (void)disableInjection {
    gDisabled = YES;
}

@end
