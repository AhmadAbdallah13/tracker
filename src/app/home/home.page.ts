import { Component, OnInit } from '@angular/core';
import { AlertController } from '@ionic/angular';

import StatsPlugin from '../statsPlugin/stats';
import { App } from '@capacitor/app';
import { Storage } from '@ionic/storage-angular';
import { BackgroundMode } from '@awesome-cordova-plugins/background-mode/ngx';

@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
})
export class HomePage implements OnInit {

  apps: any[] = []; // List of apps with usage stats
  trackedApps: Set<string> = new Set(); // Tracked apps for blocking

  private appStateListener: any;
  isBlocked: boolean = false;
  blockStartTime: number | null = null;
  // cooldownDuration: number = 60 * 60 * 1000; // 1 hour in milliseconds
  cooldownDuration: number = 120000; // 2 minutes

  constructor(
    private alertController: AlertController,
    private storage: Storage,
    private backgroundMode: BackgroundMode,
  ) {}

  async ngOnInit() {
    this.checkAccess();

    this.backgroundMode.enable();
    this.backgroundMode.disableBatteryOptimizations()
    this.backgroundMode.on('activate').subscribe(() => {
      console.error('App is running in the background');
      this.startTracking();
    });

    await this.storage.create();

    this.loadInstalledApps();

    this.appStateListener = App.addListener('appStateChange', (state) => {
      if (state.isActive) {
        this.checkAccess();
        this.loadInstalledApps();
      }
    });
  }

  startTracking() {
    setInterval(async () => {
      try {
        console.error("shit called")
        console.error(this.isBlocked)
        if(this.isBlocked){
          /**
           * if we are already in a block apps state, just block the apps without checking the stats.
           */
          const result = await StatsPlugin.periodicCalls();
          let app = result.currentApp

          console.error("shit after blocking the apps")
          console.error(app)
          console.error(this.trackedApps.has(app))

          if (this.trackedApps.has(app)){
            await StatsPlugin.showOverlay();
          }
        } else {
          const result = await StatsPlugin.periodicCalls();

          let app = result.currentApp
          let duration = result.duration

          if (this.trackedApps.has(app)){
            // if (duration > 10 * 60 * 1000 ) { 
            if (duration > 120000 ) {
              this.startBlock()
              await StatsPlugin.showOverlay();
            }
          }
        }
      } catch (error) {
        console.error("Error fetching current app:", error);
      }
    }, 1000);
  }

  // Start tracking when blocking begins
  startBlock() {
    this.isBlocked = true;
    this.blockStartTime = Date.now();
    console.error("shit")
    console.error(this.blockStartTime)
    console.error(Date.now() - this.blockStartTime)
    this.startCooldownCheck();
  }

  // Lift the block after cooldown
  liftBlock() {
    this.isBlocked = false;
    this.blockStartTime = null;
    StatsPlugin.liftBlockNotification()
  }

  startCooldownCheck() {
    // const interval = setInterval(() => {
    //   if (this.isBlocked && this.blockStartTime) {
    //     const elapsed = Date.now() - this.blockStartTime;
  
    //     if (elapsed >= this.cooldownDuration) {
    //       this.liftBlock();
    //       clearInterval(interval); // Stop the periodic check
    //     }
    //   }
    // }, 1000);
  }

  async checkAccess() {
    const result = await StatsPlugin.getUsageAccess();
    if (result.granted) {
      console.log('Usage access granted');
      return true;
    } else {
        return await new Promise(async (resolve) => {
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
    await this.loadTrackedApps()

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
    const trackedAppsArray = Array.from(this.trackedApps);
    await this.storage.remove(
      'trackedApps',
    );

    await this.storage.set(
      'trackedApps',
      JSON.stringify(trackedAppsArray),
    );
  }

  async loadTrackedApps() {
    await this.storage.get(
      'trackedApps'
    ).then((trackedApps) => {
      this.trackedApps = new Set(JSON.parse(trackedApps))
    }).catch((reason) => {
      alert("can't get tracked apps in load tracked apps function")
    })
  }

  formatTime(milliseconds: number): string {
    const minutes = Math.floor(milliseconds / 60000);
    const hours = Math.floor(minutes / 60);
    return `${hours}h ${minutes % 60}m`;
  }

}
