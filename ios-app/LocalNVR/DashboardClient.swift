import Foundation

enum DashboardClient {
    static func postOffer(serverURL: URL, streamID: String, sdp: String) async throws -> String {
        guard streamID.range(of: #"^[A-Za-z0-9_-]+$"#, options: .regularExpression) != nil else {
            throw DashboardError.message("Invalid camera stream ID.")
        }

        let endpoint = serverURL
            .appendingPathComponent("api")
            .appendingPathComponent("whep")
            .appendingPathComponent(streamID)
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/sdp", forHTTPHeaderField: "Content-Type")
        request.setValue("application/sdp", forHTTPHeaderField: "Accept")
        request.httpBody = Data(sdp.utf8)
        request.timeoutInterval = 20

        let (data, response) = try await URLSession.shared.data(for: request)
        try DashboardModel.validate(response: response, data: data)
        return String(decoding: data, as: UTF8.self)
    }
}
