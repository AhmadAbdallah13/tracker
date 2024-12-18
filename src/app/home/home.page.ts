import { Component, OnInit } from '@angular/core';
import { AlertController } from '@ionic/angular';

import StatsPlugin from '../statsPlugin/stats';
import { App } from '@capacitor/app';


@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
})
export class HomePage {

  private appStateListener: any;

  constructor(
    private alertController: AlertController,
  ) {}

  ngOnInit() {
    this.appStateListener = App.addListener('appStateChange', (state) => {
      if (state.isActive) {
        this.checkAccess();
      }
    });
  }

  async checkAccess() {
    const result = await StatsPlugin.getUsageAccess();
    if (result.granted) {
      console.log('Usage access granted');
      return true
    } else {
        return new Promise(async (resolve) => {
          const confirm = await this.alertController.create({
            header: 'Permission',
            message: 'We need usage access, otherwise we can\'t work at all. Give it or get out.',
            buttons: [
              {
                text: 'Give Permission',
                handler: () => {
                  StatsPlugin.grantUsageAccess()
                  return resolve(true);
                },
              },
              {
                text: 'Get out',
                role: 'cancel',
                handler: () => {
                  App.exitApp();;
                },
              },
            ],
          });
          await confirm.present();
        });
    }
  }

}
