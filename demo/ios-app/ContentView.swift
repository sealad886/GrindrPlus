// ContentView.swift - GrindrPlus Toolkit for iOS
// Proof of concept for iOS alternative solution

import SwiftUI
import CoreLocation

struct ContentView: View {
    @StateObject private var locationManager = LocationBookmarkManager()
    @StateObject private var safetyManager = SafetyManager() 
    
    var body: some View {
        TabView {
            LocationView()
                .tabItem {
                    Image(systemName: "location.circle.fill")
                    Text("Locations")
                }
            
            SafetyView()
                .tabItem {
                    Image(systemName: "shield.checkered")
                    Text("Safety")
                }
            
            ProfileToolsView()
                .tabItem {
                    Image(systemName: "person.circle")
                    Text("Profile")
                }
            
            AnalyticsView()
                .tabItem {
                    Image(systemName: "chart.bar.fill")
                    Text("Analytics")
                }
        }
        .accentColor(.orange)
    }
}

// Quick demo of location bookmarking
struct LocationView: View {
    @StateObject private var manager = LocationBookmarkManager()
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("🎯 Location Bookmarks")
                    .font(.title2)
                    .fontWeight(.bold)
                
                Text("Save and manage your favorite locations for quick access")
                    .multilineTextAlignment(.center)
                    .foregroundColor(.secondary)
                
                // Demo saved locations
                VStack(alignment: .leading, spacing: 10) {
                    LocationRow(name: "Home", category: "🏠", distance: "0 mi")
                    LocationRow(name: "Downtown Coffee", category: "☕", distance: "2.3 mi")
                    LocationRow(name: "Gym", category: "💪", distance: "1.7 mi")
                    LocationRow(name: "Friend's Place", category: "👥", distance: "4.1 mi")
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
                
                Button("Add New Location") {
                    // Would open location picker
                }
                .buttonStyle(.borderedProminent)
                
                Spacer()
            }
            .padding()
            .navigationTitle("Locations")
        }
    }
}

struct LocationRow: View {
    let name: String
    let category: String
    let distance: String
    
    var body: some View {
        HStack {
            Text(category)
                .font(.title2)
            
            VStack(alignment: .leading) {
                Text(name)
                    .fontWeight(.medium)
                Text(distance)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Button("Go") {
                // Would open location change guide
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
    }
}

// Safety check-in demo
struct SafetyView: View {
    var body: some View {
        NavigationView {
            VStack(spacing: 30) {
                Text("🛡️ Safety First")
                    .font(.title2)
                    .fontWeight(.bold)
                
                Text("Let trusted contacts know you're safe during meetups")
                    .multilineTextAlignment(.center)
                    .foregroundColor(.secondary)
                
                // Quick check-in button
                Button(action: {}) {
                    HStack {
                        Image(systemName: "checkmark.shield.fill")
                        Text("Quick Check-In (1 hour)")
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.green)
                    .foregroundColor(.white)
                    .cornerRadius(12)
                }
                
                // Emergency alert button
                Button(action: {}) {
                    HStack {
                        Image(systemName: "exclamationmark.triangle.fill")
                        Text("Emergency Alert")
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.red)
                    .foregroundColor(.white)
                    .cornerRadius(12)
                }
                
                // Demo active check-in
                VStack(alignment: .leading, spacing: 8) {
                    Text("Active Check-In")
                        .font(.headline)
                    
                    HStack {
                        Image(systemName: "clock.fill")
                            .foregroundColor(.orange)
                        Text("Started 15 min ago • Ends in 45 min")
                        Spacer()
                        Button("Complete") {}
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                    }
                    
                    Text("Contact: Sarah (Emergency Contact)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
                
                Spacer()
            }
            .padding()
            .navigationTitle("Safety")
        }
    }
}

// Profile tools demo
struct ProfileToolsView: View {
    var body: some View {
        NavigationView {
            List {
                Section("Health & Fitness") {
                    HStack {
                        Image(systemName: "figure.stand")
                        VStack(alignment: .leading) {
                            Text("BMI Calculator")
                            Text("Current: 23.4 (Normal)")
                                .font(.caption)
                                .foregroundColor(.green)
                        }
                    }
                }
                
                Section("Profile Optimization") {
                    HStack {
                        Image(systemName: "camera.fill")
                        VStack(alignment: .leading) {
                            Text("Photo Analyzer")
                            Text("4/6 photos, add full-body shot")
                                .font(.caption)
                                .foregroundColor(.orange)
                        }
                    }
                    
                    HStack {
                        Image(systemName: "text.bubble.fill")
                        VStack(alignment: .leading) {
                            Text("Profile Tips")
                            Text("85% profile completeness")
                                .font(.caption)
                                .foregroundColor(.blue)
                        }
                    }
                }
                
                Section("Saved Phrases") {
                    Text("Hey! How's your day going?")
                    Text("Would you like to grab coffee sometime?")
                    Text("Thanks for the chat! Have a great day")
                }
            }
            .navigationTitle("Profile Tools")
        }
    }
}

// Analytics demo
struct AnalyticsView: View {
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    Text("📊 Your Insights")
                        .font(.title2)
                        .fontWeight(.bold)
                    
                    // Stats cards
                    LazyVGrid(columns: [
                        GridItem(.flexible()),
                        GridItem(.flexible())
                    ], spacing: 16) {
                        StatCard(title: "Profile Views", value: "127", subtitle: "This week", color: .blue)
                        StatCard(title: "Messages", value: "43", subtitle: "This week", color: .green)
                        StatCard(title: "Meetups", value: "3", subtitle: "This month", color: .purple)
                        StatCard(title: "Safety Check-ins", value: "12", subtitle: "All time", color: .orange)
                    }
                    
                    // Usage insights
                    VStack(alignment: .leading, spacing: 12) {
                        Text("This Week's Activity")
                            .font(.headline)
                        
                        Text("🎯 Most active time: 7-9 PM")
                        Text("📍 Favorite location: Downtown Coffee")
                        Text("💬 Avg response time: 12 minutes")
                        Text("⭐ Profile completion: 85%")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                }
                .padding()
            }
            .navigationTitle("Analytics")
        }
    }
}

struct StatCard: View {
    let title: String
    let value: String
    let subtitle: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 8) {
            Text(value)
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(color)
            
            Text(title)
                .font(.caption)
                .fontWeight(.medium)
            
            Text(subtitle)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}

// Placeholder classes for demo
class LocationBookmarkManager: ObservableObject {}
class SafetyManager: ObservableObject {}

#Preview {
    ContentView()
}