import { Component, OnInit } from '@angular/core';
import { AlertController } from '@ionic/angular';

import StatsPlugin from '../statsPlugin/stats';
import { App } from '@capacitor/app';
import { StorageService } from '../services/storage-service.service';

@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
})
export class HomePage implements OnInit {

  apps: any[] = []; // List of apps with usage stats
  trackedApps: Set<string> = new Set(); // Tracked apps for blocking

  private appStateListener: any;

  constructor(
    private alertController: AlertController,
    private storageService: StorageService,
  ) {}

  ngOnInit() {
    this.checkAccess();
    this.loadInstalledApps();

    this.appStateListener = App.addListener('appStateChange', (state) => {
      if (state.isActive) {
        this.checkAccess();
        this.loadInstalledApps();
      }
    });

  }

  async checkAccess() {
    const result = await StatsPlugin.getUsageAccess();
    if (result.granted) {
      console.log('Usage access granted');
      return true;
    } else {
        return new Promise(async (resolve) => {
          const confirm = await this.alertController.create({
            header: 'Permission',
            message: 'We need usage access, otherwise we can\'t work at all. Give it or get out.',
            buttons: [
              {
                text: 'Give Permission',
                handler: () => {
                  StatsPlugin.grantUsageAccess();
                  return resolve(true);
                },
              },
              {
                text: 'Get out',
                role: 'cancel',
                handler: () => {
                  App.exitApp();
                },
              },
            ],
          });
          await confirm.present();
        });
    }
  }

  async loadInstalledApps() {
    this.loadTrackedApps()

    const result = await StatsPlugin.getInstalledApps();
    this.apps = result.apps.map((app: any) => ({
      ...app,
      isTracked: this.trackedApps.has(app.packageName),
    }));
  }

  async toggleAppTracking(app: any) {
    if (this.trackedApps.has(app.packageName)) {
      this.trackedApps.delete(app.packageName);
    } else {
      this.trackedApps.add(app.packageName);
    }
    app.isTracked = !app.isTracked;

    // Persist the updated tracked apps
    await this.saveTrackedApps();
  }

  async saveTrackedApps() {
    console.log("shit tracked")
    console.log(this.trackedApps)

    const trackedAppsArray = Array.from(this.trackedApps);
    await this.storageService.set(
      'trackedApps',
      JSON.stringify(trackedAppsArray),
    );

    console.log("shit after")
    console.log(await this.storageService.get('trackedApps'))
  }

  async loadTrackedApps() {
    // console.log("shit")
    const value = await this.storageService.get('trackedApps');
    // console.log("shit after")
    // console.log(value)

    if (value) {
      this.trackedApps = new Set(JSON.parse(value));
    }
  }

  formatTime(milliseconds: number): string {
    const minutes = Math.floor(milliseconds / 60000);
    const hours = Math.floor(minutes / 60);
    return `${hours}h ${minutes % 60}m`;
  }

}
