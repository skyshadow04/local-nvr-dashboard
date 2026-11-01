import Foundation
import Combine

struct Camera: Decodable, Identifiable, Hashable {
    let id: String
    let name: String
    let type: String
    let ip: String
    let channel: Int
    let stream: String
}

private struct CameraListResponse: Decodable {
    let cameras: [Camera]
}

@MainActor
final class DashboardModel: ObservableObject {
    @Published var serverAddress: String
    @Published private(set) var cameras: [Camera] = []
    @Published private(set) var isLoading = false
    @Published var message: String?

    private let defaults = UserDefaults.standard

    init() {
        serverAddress = defaults.string(forKey: "dashboard-server-address") ?? ""
    }

    func connect() async {
        isLoading = true
        message = nil
        defer { isLoading = false }

        do {
            let baseURL = try Self.normalizedBaseURL(serverAddress)
            let endpoint = baseURL.appendingPathComponent("api").appendingPathComponent("stream")
            var request = URLRequest(url: endpoint)
            request.cachePolicy = .reloadIgnoringLocalCacheData
            request.timeoutInterval = 15
            let (data, response) = try await URLSession.shared.data(for: request)
            try Self.validate(response: response, data: data)

            let result = try JSONDecoder().decode(CameraListResponse.self, from: data)
            cameras = result.cameras
            serverAddress = baseURL.absoluteString
            defaults.set(serverAddress, forKey: "dashboard-server-address")
            if cameras.isEmpty {
                message = "No cameras are configured. Add cameras from the web dashboard first."
            }
        } catch {
            cameras = []
            message = error.localizedDescription
        }
    }

    func baseURL() throws -> URL {
        try Self.normalizedBaseURL(serverAddress)
    }

    private static func normalizedBaseURL(_ value: String) throws -> URL {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !trimmed.isEmpty else {
            throw DashboardError.message("Enter the dashboard address.")
        }

        let address = trimmed.contains("://") ? trimmed : "http://\(trimmed)"
        guard let components = URLComponents(string: address),
              let scheme = components.scheme?.lowercased(),
              ["http", "https"].contains(scheme),
              components.host != nil,
              components.user == nil,
              components.password == nil,
              components.query == nil,
              components.fragment == nil,
              components.path.isEmpty || components.path == "/",
              let url = components.url else {
            throw DashboardError.message("Enter a dashboard root URL, such as http://100.x.y.z:3001.")
        }
        return url
    }

    static func validate(response: URLResponse, data: Data) throws {
        guard let response = response as? HTTPURLResponse else {
            throw DashboardError.message("The dashboard returned an invalid response.")
        }
        guard (200..<300).contains(response.statusCode) else {
            let payload = try? JSONDecoder().decode(ErrorResponse.self, from: data)
            throw DashboardError.message(
                payload?.error ?? "Dashboard request failed (\(response.statusCode))."
            )
        }
    }
}

private struct ErrorResponse: Decodable {
    let error: String
}

enum DashboardError: LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case .message(let text): return text
        }
    }
}
