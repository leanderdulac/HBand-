import Foundation

@main
struct WatchScanFilterTests {
    static func main() {
        var failures = 0
        func expect(_ cond: @autoclosure () -> Bool, _ msg: String) {
            if !cond() {
                fputs("FAIL: \(msg)\n", stderr)
                failures += 1
            }
        }

        let fee7 = "0000fee7-0000-1000-8000-00805f9b34fb"
        let nordic = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
        let ffff = "0000ffff-0000-1000-8000-00805f9b34fb"
        let service0001 = "00000001-0000-1000-8000-00805f9b34fb"
        let heartRate = "0000180d-0000-1000-8000-00805f9b34fb"
        let savedMac = "AA:BB:CC:DD:EE:FF"
        let savedUuid = "E621E1F8-C36C-495A-93FC-0C247A3E6E5F"

        for name in [
            "VE30", "ve30", "Ve30", "VE 30", "VE-30", "VE_30", "  VE30  ",
            "HBand", "H Band", "H-Band", "h_band", "Veepoo", "veepoo band", "VEEPOO",
        ] {
            expect(WatchScanFilter.shouldList(name: name), "nome compatível: \(name)")
        }

        for name in [
            "AirPods Pro", "AIRPODS", "Galaxy Buds", "JBL Flip 6", "Fone Bluetooth",
            "Samsung TV", "[TV] Samsung", "Smart TV LG", "iPhone", "iPhone de Maria",
            "Galaxy Watch8", "Galaxy Watch 8", "Gear S3", "Wear OS", "SM-R860",
            "Pulseira", "Relógio",
        ] {
            expect(!WatchScanFilter.shouldList(name: name), "dispositivo comum: \(name)")
        }

        expect(!WatchScanFilter.shouldList(name: nil), "nome nulo")
        expect(!WatchScanFilter.shouldList(name: ""), "nome vazio")
        expect(!WatchScanFilter.shouldList(name: "   "), "nome em branco")
        expect(!WatchScanFilter.shouldList(name: "\n"), "nome só com quebra")
        expect(WatchScanFilter.unnamedWatchLabel == "Relógio", "rótulo sem nome")

        for uuid in [fee7, "FEE7", nordic.uppercased(), ffff, "0001", service0001] {
            expect(WatchScanFilter.shouldList(name: nil, serviceUuids: [uuid]), "serviço \(uuid) sem nome")
            expect(WatchScanFilter.shouldList(name: "Ana", serviceUuids: [uuid]), "serviço \(uuid) com nome próprio")
        }
        expect(
            WatchScanFilter.shouldList(name: "Watch", serviceUuids: ["0000FEE7-0000-1000-8000-00805F9B34FB"]),
            "FEE7 em maiúsculas"
        )
        expect(!WatchScanFilter.shouldList(name: "Polar H10", serviceUuids: ["180D"]), "180D curto não lista")
        expect(!WatchScanFilter.shouldList(name: nil, serviceUuids: [heartRate]), "180D completo não lista")
        expect(!WatchScanFilter.shouldList(name: "Fone", serviceUuids: ["FFF0", "180A"]), "FFF0/180A sozinhos não listam")

        let veepoo = Data([0xF8, 0xF8, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06])
        let veepooAlt = Data([0xF8, 0xF9, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06])
        let shortVeepoo = Data([0xF8, 0xF8, 0x01, 0x02, 0x03, 0x04, 0x05])
        let apple = Data([0x4C, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06])
        expect(WatchScanFilter.hasVeepooManufacturer(veepoo), "f8f8 com 8 bytes")
        expect(WatchScanFilter.hasVeepooManufacturer(veepooAlt), "f8f9 com 8 bytes")
        expect(!WatchScanFilter.hasVeepooManufacturer(shortVeepoo), "f8f8 curto demais")
        expect(!WatchScanFilter.hasVeepooManufacturer(apple), "fabricante Apple")
        expect(!WatchScanFilter.hasVeepooManufacturer(nil), "sem fabricante")
        expect(WatchScanFilter.shouldList(name: "Ana", manufacturerData: veepoo), "renomeado com f8f8")
        expect(WatchScanFilter.shouldList(name: nil, manufacturerData: veepooAlt), "sem nome com f8f9")
        expect(!WatchScanFilter.shouldList(name: "AirPods Pro", manufacturerData: apple), "AirPods")
        expect(!WatchScanFilter.shouldList(name: "Fone Bluetooth", manufacturerData: shortVeepoo), "anúncio curto")

        expect(
            WatchScanFilter.shouldList(name: "Ana", address: "aa:bb:cc:dd:ee:ff", knownAddresses: [savedMac]),
            "MAC salvo, outro nome"
        )
        expect(
            WatchScanFilter.shouldList(name: "Samsung TV", address: savedMac, knownAddresses: ["  \(savedMac)  "]),
            "MAC salvo ignora o nome atual"
        )
        expect(
            !WatchScanFilter.shouldList(name: "Samsung TV", address: "11:22:33:44:55:66", knownAddresses: [savedMac]),
            "outro MAC não entra"
        )
        expect(
            WatchScanFilter.shouldList(name: "Televisão", uuid: savedUuid.lowercased(), knownUuids: [savedUuid]),
            "UUID salvo, maiúsculas diferentes"
        )
        expect(
            !WatchScanFilter.shouldList(name: nil, address: "", knownAddresses: [""]),
            "endereço vazio não é relógio conhecido"
        )
        expect(
            !WatchScanFilter.shouldList(name: "AirPods Pro", uuid: nil, knownUuids: [savedUuid]),
            "sem UUID não herda o relógio salvo"
        )
        expect(
            WatchScanFilter.shouldList(name: "Ana", alreadyConnectedOnVeepooService: true),
            "já ligado num serviço Veepoo"
        )
        expect(
            !WatchScanFilter.shouldList(name: "AirPods Pro", alreadyConnectedOnVeepooService: false),
            "ligado noutro serviço não entra só por isso"
        )

        expect(
            WatchScanFilter.patientHint == "A lista mostra só relógios compatíveis com este aplicativo.",
            "texto para o paciente"
        )
        expect(!WatchScanFilter.patientHint.contains("UUID"), "sem UUID na frase")
        expect(!WatchScanFilter.patientHint.lowercased().contains("f8"), "sem ID técnico na frase")

        if failures > 0 {
            fputs("\(failures) falha(s)\n", stderr)
            exit(1)
        }
        fputs("ok\n", stdout)
    }
}
